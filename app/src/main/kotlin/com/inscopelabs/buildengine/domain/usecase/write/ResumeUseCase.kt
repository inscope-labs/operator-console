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

/**
 * Target for clearing a repository-wide halt (`POST /resume`, §3.12; T3-401, PR3-024).
 *
 * Unlike [HaltTarget], [ResumeUseCase] does not accept an arbitrary caller-supplied reason:
 * it requires the recorded halt cause persisted in the encrypted local operation journal.
 */
data class ResumeTarget(
    val plan: String,
    val order: String,
    val headSha: String,
    val branch: String = HaltTarget.ENGINE_BRANCH,
    val task: String = HaltTarget.ENGINE_TASK,
)

sealed interface ResumeResult {
    data class Executed(
        val outcome: OperationOutcome,
        val authoritativeHalted: Boolean? = null,
    ) : ResumeResult

    /** Refused before minting or sending because no prior recorded halt cause exists in the journal. */
    data class MissingRecordedCause(val reason: String) : ResumeResult
    data class NotAuthorized(val reason: MintResult) : ResumeResult
    data class InvalidTarget(val reason: String) : ResumeResult
}

/**
 * Clears the repository-wide halt marker (`POST /resume`, §3.12).
 *
 * Refuses before minting when no recorded halt cause exists in the encrypted operation journal
 * (PR3-024 / T3-401), enforces engine-branch capability scoping, routes through
 * [ConfirmAndMintCapabilityUseCase], and checks authoritative plan status after verified success.
 */
class ResumeUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
    private val planRepository: PlanRepository? = null,
) {

    suspend operator fun invoke(
        target: ResumeTarget,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): ResumeResult {
        if (target.plan.isBlank() || target.task.isBlank() || target.order.isBlank()) {
            return ResumeResult.InvalidTarget("Plan, task and order identifiers are required")
        }

        val scope = try {
            CapabilityScope(
                op = OperationType.RESUME,
                branch = target.branch,
                head = target.headSha,
                nonce = nonceSource.newNonce(),
            )
        } catch (e: IllegalArgumentException) {
            return ResumeResult.InvalidTarget(e.message ?: "Invalid resume scope")
        }

        val recordedCause = operationRepository.recordedHaltCause()?.trim().orEmpty()
        if (recordedCause.isEmpty()) {
            return ResumeResult.MissingRecordedCause(
                "Cannot resume: no recorded halt cause found in the local encrypted operation journal",
            )
        }

        val request = OperationRequest(
            op = OperationType.RESUME,
            scope = scope,
            plan = target.plan,
            task = target.task,
            order = target.order,
            typedHeadConfirmation = typedHeadConfirmation,
        )

        val minted = when (val result = confirmAndMintCapability(request, policy)) {
            is MintResult.Minted -> result
            else -> return ResumeResult.NotAuthorized(result)
        }

        val outcome = operationRepository.resume(
            request = request,
            reason = recordedCause,
            capability = minted.capability,
        )
        val authoritativeHalted = if (outcome is OperationOutcome.Succeeded && planRepository != null) {
            (planRepository.getStatus(target.plan) as? AppResult.Success)?.value?.halted
        } else {
            null
        }
        return ResumeResult.Executed(outcome = outcome, authoritativeHalted = authoritativeHalted)
    }
}
