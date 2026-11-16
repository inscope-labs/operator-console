package com.inscopelabs.buildengine.manualpipeline.domain.port

/**
 * Read-only port for Gateway A audit endpoints (`GET /audit`, `GET /audit/{ref}`).
 */
interface AuditReadPort {
    suspend fun fetchRecentAudit(limit: Int = 50): Result<List<AuditEntry>>
    suspend fun fetchAuditByRef(auditRef: String): Result<AuditEntry>
}

data class AuditEntry(
    val auditRef: String,
    val timestampEpochMs: Long,
    val operation: String,
    val planId: String,
    val taskId: String,
    val branch: String,
    val headSha: String,
    val outcome: String,
    val code: String? = null,
)
