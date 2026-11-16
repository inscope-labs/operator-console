package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * `PRE_TRANSFER_HANDOFF_READY` evidence bundle (MP-014).
 * Captures verified post-upload / post-transition read-back references without any secret material.
 */
data class HandoffEvidence(
    val bundleId: String,
    val taskId: String,
    val planId: String,
    val orderId: String,
    val branch: String,
    val verifiedHeadSha: String,
    val submissionId: String,
    val zipSha256: String,
    val uploadAuditRef: String,
    val transitionAuditRef: String,
    val uploadIdempotencyKey: String,
    val transitionIdempotencyKey: String,
    val observedTaskStatus: TaskStatus,
    val createdAtEpochMs: Long,
    val readinessState: String = READY_MARKER,
) {
    val isReady: Boolean
        get() = readinessState == READY_MARKER &&
            verifiedHeadSha.matches(Regex("^[0-9a-fA-F]{40}$")) &&
            uploadAuditRef.isNotBlank() &&
            transitionAuditRef.isNotBlank()

    companion object {
        const val READY_MARKER = "PRE_TRANSFER_HANDOFF_READY"
    }
}
