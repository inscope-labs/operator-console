package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Status of lookup-only recovery reconciliation (`GET /operations/{id}` + status read-back).
 */
data class RecoveryStatus(
    val idempotencyKey: String,
    val state: RecoveryState,
    val gatewayOperationStatus: String? = null,
    val auditRef: String? = null,
    val headSha: String? = null,
    val errorCode: String? = null,
    val detail: String = "",
)

enum class RecoveryState {
    IDLE,
    RECONCILING,
    RESOLVED_SUCCEEDED,
    RESOLVED_REFUSED,
    RESOLVED_CONFLICT,
    PENDING_RETRY,
    MANUAL_REVIEW_REQUIRED,
}
