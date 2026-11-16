package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Module-local non-secret operator action log entry (MP-013).
 */
data class OperatorActionRecord(
    val id: String,
    val taskId: String,
    val planId: String,
    val actionName: String,
    val outcomeLabel: String,
    val idempotencyKey: String?,
    val auditRef: String?,
    val headSha: String?,
    val detail: String,
    val timestampEpochMs: Long,
)
