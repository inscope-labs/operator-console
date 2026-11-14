package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult

/** Target for `POST /tasks/{id}/reconcile` (§3.9; T3-402, PR3-023). */
data class ReconcileTarget(
    val taskId: String,
    val plan: String,
    val order: String,
    val branch: String,
    val headSha: String,
    val submissionId: String,
)

/** Gateway merge/close disposition surfaced from verified reconcile completion. */
sealed interface ReconcileDisposition {
    data class Merged(val rawOutcome: String) : ReconcileDisposition
    data class Closed(val rawOutcome: String) : ReconcileDisposition
    data class Other(val rawOutcome: String) : ReconcileDisposition
    data object Unreported : ReconcileDisposition

    companion object {
        fun fromGatewayOutcome(rawOutcome: String?): ReconcileDisposition {
            val trimmed = rawOutcome?.trim().orEmpty()
            return when {
                trimmed.isEmpty() -> Unreported
                trimmed.startsWith("MERGED", ignoreCase = true) -> Merged(trimmed)
                trimmed.startsWith("CLOSED", ignoreCase = true) -> Closed(trimmed)
                else -> Other(trimmed)
            }
        }
    }
}

sealed interface ReconcileResult {
    data class Executed(
        val outcome: OperationOutcome,
        val disposition: ReconcileDisposition?,
    ) : ReconcileResult

    data class NotAuthorized(val reason: MintResult) : ReconcileResult
    data class InvalidTarget(val reason: String) : ReconcileResult
}

/**
 * Reconciles merge status after operator review (`POST /tasks/{id}/reconcile`, §3.9).
 *
 * The console never merges locally; it mints a `RECONCILE`-scoped capability via
 * [ConfirmAndMintCapabilityUseCase], executes through the journal-backed repository, and
 * surfaces any `MERGED:` / `CLOSED:` outcome reported by the gateway.
 */
class ReconcileUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
) {

    suspend operator fun invoke(
        target: ReconcileTarget,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): ReconcileResult {
        val trimmedSubmissionId = target.submissionId.trim()
        if (target.plan.isBlank() || target.taskId.isBlank() || target.order.isBlank() || trimmedSubmissionId.isEmpty()) {
            return ReconcileResult.InvalidTarget("Plan, task, order and submissionId are required")
        }

        val scope = try {
            CapabilityScope(
                op = OperationType.RECONCILE,
                branch = target.branch,
                head = target.headSha,
                nonce = nonceSource.newNonce(),
            )
        } catch (e: IllegalArgumentException) {
            return ReconcileResult.InvalidTarget(e.message ?: "Invalid reconcile scope")
        }

        val request = OperationRequest(
            op = OperationType.RECONCILE,
            scope = scope,
            plan = target.plan,
            task = target.taskId,
            order = target.order,
            typedHeadConfirmation = typedHeadConfirmation,
        )

        val minted = when (val result = confirmAndMintCapability(request, policy)) {
            is MintResult.Minted -> result
            else -> return ReconcileResult.NotAuthorized(result)
        }

        val outcome = operationRepository.reconcile(
            request = request,
            submissionId = trimmedSubmissionId,
            capability = minted.capability,
        )
        val disposition = if (outcome is OperationOutcome.Succeeded) {
            ReconcileDisposition.fromGatewayOutcome(outcome.outcome)
        } else {
            null
        }
        return ReconcileResult.Executed(outcome = outcome, disposition = disposition)
    }
}
