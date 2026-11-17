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

/** Target for `POST /plans/{id}/status` (§3.17; T3-404, PR3-026, ADR 0016 D-1). */
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

    data class UnsupportedByContract(val reason: String) : SetTaskStatusResult
    data class InvalidTransition(val reason: String) : SetTaskStatusResult
    data class NotAuthorized(val reason: MintResult) : SetTaskStatusResult
    data class InvalidTarget(val reason: String) : SetTaskStatusResult
}

/**
 * Operator-executed task status change (`POST /plans/{id}/status`, §3.17).
 *
 * Per ADR 0016 D-1 (contract revision 1.3), only `SET_IN_PROGRESS` (`PENDING -> IN_PROGRESS`) is
 * supported; `completed` (`SET_DONE`) and `SET` are reserved and rejected with
 * [SetTaskStatusResult.UnsupportedByContract] before any biometric prompt or gateway request.
 * Obtains its scoped capability solely via [ConfirmAndMintCapabilityUseCase], executes through the
 * journal-backed [OperationRepository], and refreshes authoritative task/plan state after verified
 * success.
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

        if (target.targetStatus == TaskStatus.DONE) {
            return SetTaskStatusResult.UnsupportedByContract(
                "Task completion (SET_DONE / completed) is deferred and reserved under contract revision 1.3 (ADR 0016 D-1).",
            )
        }

        val transitionSpec = resolveTransition(target.currentStatus, target.targetStatus)
            ?: return SetTaskStatusResult.InvalidTransition(
                "Transition from ${target.currentStatus} to ${target.targetStatus} is not permitted",
            )

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
            else -> null
        }
}
