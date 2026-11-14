package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.util.Redactor

/**
 * Target for a repository-wide halt scoped strictly to the non-task `engine` branch (T3-401, PR3-024).
 */
data class HaltTarget(
    val plan: String,
    val order: String,
    val headSha: String,
    val reason: String,
    val branch: String = ENGINE_BRANCH,
    val task: String = ENGINE_TASK,
) {
    companion object {
        const val ENGINE_BRANCH: String = "engine"
        const val ENGINE_TASK: String = "engine"
    }
}

sealed interface HaltResult {
    data class Executed(
        val outcome: OperationOutcome,
        val authoritativeHalted: Boolean? = null,
    ) : HaltResult

    data class NotAuthorized(val reason: MintResult) : HaltResult
    data class InvalidTarget(val reason: String) : HaltResult
}

/**
 * Repository-wide stop (`POST /halt`, §3.11).
 *
 * Enforces an engine-branch capability scope (never a `task-*` branch), requires a non-blank
 * halt reason, goes exclusively through [ConfirmAndMintCapabilityUseCase] + journal-backed
 * [OperationRepository.halt], and queries authoritative gateway state after verified success.
 */
class HaltUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
    private val planRepository: PlanRepository? = null,
) {

    suspend operator fun invoke(
        target: HaltTarget,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): HaltResult {
        val trimmedReason = target.reason.trim()
        if (trimmedReason.isEmpty()) {
            return HaltResult.InvalidTarget("A non-blank halt reason is required")
        }
        if (Redactor.containsForbidden(trimmedReason)) {
            return HaltResult.InvalidTarget("Halt reason must not contain forbidden credential or signature patterns")
        }
        if (target.plan.isBlank() || target.task.isBlank() || target.order.isBlank()) {
            return HaltResult.InvalidTarget("Plan, task and order identifiers are required")
        }

        val scope = try {
            CapabilityScope(
                op = OperationType.HALT,
                branch = target.branch,
                head = target.headSha,
                nonce = nonceSource.newNonce(),
            )
        } catch (e: IllegalArgumentException) {
            return HaltResult.InvalidTarget(e.message ?: "Invalid halt scope")
        }

        val request = OperationRequest(
            op = OperationType.HALT,
            scope = scope,
            plan = target.plan,
            task = target.task,
            order = target.order,
            typedHeadConfirmation = typedHeadConfirmation,
        )

        val minted = when (val result = confirmAndMintCapability(request, policy)) {
            is MintResult.Minted -> result
            else -> return HaltResult.NotAuthorized(result)
        }

        val outcome = operationRepository.halt(
            request = request,
            reason = trimmedReason,
            capability = minted.capability,
        )
        val authoritativeHalted = if (outcome is OperationOutcome.Succeeded && planRepository != null) {
            (planRepository.getStatus(target.plan) as? AppResult.Success)?.value?.halted
        } else {
            null
        }
        return HaltResult.Executed(outcome = outcome, authoritativeHalted = authoritativeHalted)
    }
}
