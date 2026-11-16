package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Domain outcome representation for manual pipeline operations.
 */
sealed interface OperationOutcome {
    data class Succeeded(
        val auditRef: String,
        val headSha: String,
        val statusLabel: String? = null,
    ) : OperationOutcome

    data class Refused(
        val code: String,
        val message: String,
        val auditRef: String? = null,
    ) : OperationOutcome

    data class Conflict(
        val code: String,
        val message: String,
        val liveHeadSha: String? = null,
        val auditRef: String? = null,
    ) : OperationOutcome

    data class Timeout(
        val idempotencyKey: String,
        val message: String = "No response from gateway; lookup recovery required.",
    ) : OperationOutcome

    data class UncertainCompletion(
        val idempotencyKey: String,
        val headSha: String? = null,
        val auditRef: String? = null,
        val message: String = "Completion not yet verified against live branch head.",
    ) : OperationOutcome

    data class RecoveryRequired(
        val idempotencyKey: String,
        val code: String? = null,
        val auditRef: String? = null,
        val message: String = "Manual lookup recovery required before continuing.",
    ) : OperationOutcome
}
