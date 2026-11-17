package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditEntry
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditReadPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading Gateway A audit evidence records (`GET /audit`, `GET /audit/{ref}`).
 */
@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val auditReadPort: AuditReadPort,
) {
    suspend fun fetchRecent(limit: Int = 50): Result<List<AuditEntry>> =
        auditReadPort.fetchRecentAudit(limit)

    suspend fun fetchByRef(auditRef: String): Result<AuditEntry> =
        auditReadPort.fetchAuditByRef(auditRef)
}
