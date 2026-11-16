package com.inscopelabs.buildengine.manualpipeline.data.gateway

import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.AuditMapper
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.GatewayAuditSummaryDto
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditEntry
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditReadPort
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * Read-only Gateway A audit stream adapter calling `GET /audit` and `GET /audit/{ref}`
 * via the existing pinned & bearer-authenticated [GatewayHttpClient] without mutating
 * frozen `GatewayEndpoints` constants.
 */
class GatewayAAuditReadAdapter @Inject constructor(
    private val httpClient: GatewayHttpClient,
) : AuditReadPort {

    override suspend fun fetchRecentAudit(limit: Int): Result<List<AuditEntry>> = runCatching {
        val raw = httpClient.get("/audit")
        val list = json.decodeFromString<List<GatewayAuditSummaryDto>>(raw)
        list.take(limit).map(AuditMapper::toDomain)
    }

    override suspend fun fetchAuditByRef(auditRef: String): Result<AuditEntry> = runCatching {
        val path = GatewayEndpoints.path("/audit/{ref}", "ref" to auditRef)
        val raw = httpClient.get(path)
        val dto = json.decodeFromString<GatewayAuditSummaryDto>(raw)
        AuditMapper.toDomain(dto)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
