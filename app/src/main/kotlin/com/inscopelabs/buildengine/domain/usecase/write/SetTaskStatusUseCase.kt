package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.util.Redactor

/** Target for `POST /plans/{id}/status` (§3.17; T3-404, PR3-026). */
data class SetTaskStatusTarget(
    val taskId: String,
    val plan: String,
    val order: String,
    val branch: String,
    val headSha: String,
    val currentStatus: TaskStatus,
    val targetStatus: TaskStatus,
    val evidence: String? = null,
)

sealed interface SetTaskStatusResult {
    data class Executed(
        val outcome: OperationOutcome,
        val authoritativeTask: Task? = null,
        val authoritativePlanStatus: PlanStatus? = null,
    ) : SetTaskStatusResult

    data class InvalidTransition(val reason: String) : SetTaskStatusResult
    data class MissingEvidence(val reason: String) : SetTaskStatusResult
    data class NotAuthorized(val reason: MintResult) : SetTaskStatusResult
    data class InvalidTarget(val reason: String) : SetTaskStatusResult
}

/**
 * Operator-executed task status change (`POST /plans/{id}/status`, §3.17).
 *
 * Enforces the allowed transition table (`PENDING -> IN_PROGRESS`, `PENDING -> DONE`,
 * `IN_PROGRESS -> DONE`), requires completion [SetTaskStatusTarget.evidence] when transitioning
 * to [TaskStatus.DONE], obtains its scoped capability (`SET_IN_PROGRESS` / `SET_DONE`) solely via
 * [ConfirmAndMintCapabilityUseCase], executes through the journal-backed [OperationRepository],
 * and refreshes authoritative task/plan state after verified success.
 */
class SetTaskStatusUseCase(
    private val operationRepository: OperationRepository,
    private val taskRepository: TaskRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
    private val planRepository: PlanRepository? = null,
) {

    suspend operator fun invoke(
        target: SetTaskStatusTarget,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): SetTaskStatusResult {
        if (target.plan.isBlank() || target.taskId.isBlank() || target.order.isBlank()) {
            return SetTaskStatusResult.InvalidTarget("Plan, task and order identifiers are required")
        }

        val transitionSpec = resolveTransition(target.currentStatus, target.targetStatus)
            ?: return SetTaskStatusResult.InvalidTransition(
                "Transition from ${target.currentStatus} to ${target.targetStatus} is not permitted",
            )

        if (target.targetStatus == TaskStatus.DONE) {
            val trimmedEvidence = target.evidence?.trim().orEmpty()
            if (trimmedEvidence.isEmpty()) {
                return SetTaskStatusResult.MissingEvidence(
                    "Marking a task done requires completion evidence",
                )
            }
            if (Redactor.containsForbidden(trimmedEvidence)) {
                return SetTaskStatusResult.InvalidTarget(
                    "Completion evidence must not contain forbidden credential or signature patterns",
                )
            }
        }

        val scope = try {
            CapabilityScope(
                op = transitionSpec.operationType,
                branch = target.branch,
                head = target.headSha,
                nonce = nonceSource.newNonce(),
            )
        } catch (e: IllegalArgumentException) {
            return SetTaskStatusResult.InvalidTarget(e.message ?: "Invalid status transition scope")
        }

        val request = OperationRequest(
            op = transitionSpec.operationType,
            scope = scope,
            plan = target.plan,
            task = target.taskId,
            order = target.order,
            typedHeadConfirmation = typedHeadConfirmation,
        )

        val minted = when (val result = confirmAndMintCapability(request, policy)) {
            is MintResult.Minted -> result
            else -> return SetTaskStatusResult.NotAuthorized(result)
        }

        val outcome = operationRepository.setTaskStatus(
            request = request,
            targetStatus = transitionSpec.wireTargetStatus,
            capability = minted.capability,
        )

        val refreshedTask = if (outcome is OperationOutcome.Succeeded) {
            (taskRepository.getTask(target.taskId) as? AppResult.Success)?.value
        } else {
            null
        }
        val refreshedPlanStatus = if (outcome is OperationOutcome.Succeeded && planRepository != null) {
            (planRepository.getStatus(target.plan) as? AppResult.Success)?.value
        } else {
            null
        }
        return SetTaskStatusResult.Executed(
            outcome = outcome,
            authoritativeTask = refreshedTask,
            authoritativePlanStatus = refreshedPlanStatus,
        )
    }

    private data class TransitionSpec(
        val operationType: OperationType,
        val wireTargetStatus: String,
    )

    private fun resolveTransition(current: TaskStatus, target: TaskStatus): TransitionSpec? =
        when (current to target) {
            TaskStatus.PENDING to TaskStatus.IN_PROGRESS ->
                TransitionSpec(OperationType.SET_IN_PROGRESS, "in_progress")
            TaskStatus.PENDING to TaskStatus.DONE,
            TaskStatus.IN_PROGRESS to TaskStatus.DONE ->
                TransitionSpec(OperationType.SET_DONE, "completed")
            else -> null
        }
}
