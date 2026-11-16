package com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper

import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditEntry
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class GatewayAuditSummaryDto(
    val auditRef: String = "",
    val timestamp: String = "",
    val operation: String = "",
    val plan: String? = null,
    val task: String? = null,
    val branch: String? = null,
    val headSha: String? = null,
    val outcome: String = "",
    val code: String? = null,
)

object AuditMapper {

    fun toDomain(dto: GatewayAuditSummaryDto): AuditEntry {
        val epochMs = parseTimestampEpochMs(dto.timestamp)
        return AuditEntry(
            auditRef = dto.auditRef,
            timestampEpochMs = epochMs,
            operation = dto.operation,
            planId = dto.plan.orEmpty(),
            taskId = dto.task.orEmpty(),
            branch = dto.branch.orEmpty(),
            headSha = dto.headSha.orEmpty(),
            outcome = dto.outcome,
            code = dto.code,
        )
    }

    private fun parseTimestampEpochMs(raw: String): Long {
        val clean = raw.trim()
        if (clean.isEmpty()) return 0L
        clean.toLongOrNull()?.let { num ->
            return if (num < 10_000_000_000L) num * 1000L else num
        }
        return runCatching { Instant.parse(clean).toEpochMilli() }.getOrDefault(0L)
    }
}
