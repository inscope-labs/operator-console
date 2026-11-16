package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Non-secret view of an encrypted journaled operation record (ADR 0009, ADR 0013).
 * Never carries capabilities, signatures, nonces, or raw upload bytes.
 */
data class OperationRecord(
    val idempotencyKey: String,
    val operationName: String,
    val planId: String,
    val taskId: String,
    val branch: String,
    val expectedHead: String,
    val state: String,
    val auditRef: String? = null,
    val resultHeadSha: String? = null,
    val lastErrorCode: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
