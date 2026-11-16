package com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper

import com.inscopelabs.buildengine.core.network.dto.OperationLookupDto
import com.inscopelabs.buildengine.domain.model.OperationOutcome as HostOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome as DomainOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryState
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus

object OperationMapper {

    fun toDomainOutcome(host: HostOutcome): DomainOutcome = when (host) {
        is HostOutcome.Succeeded -> DomainOutcome.Succeeded(
            auditRef = host.auditRef,
            headSha = host.headSha,
            statusLabel = host.outcome,
        )
        is HostOutcome.Refused -> DomainOutcome.Refused(
            code = host.code,
            message = "Gateway refused operation (${host.code}).",
            auditRef = host.auditRef,
        )
        is HostOutcome.Conflict -> DomainOutcome.Conflict(
            code = host.code,
            message = "Gateway concurrency/seal conflict (${host.code}).",
            auditRef = host.auditRef,
        )
        is HostOutcome.Timeout -> DomainOutcome.Timeout(
            idempotencyKey = host.idempotencyKey,
        )
        is HostOutcome.UncertainCompletion -> DomainOutcome.UncertainCompletion(
            idempotencyKey = host.idempotencyKey,
            headSha = host.headSha,
            auditRef = host.auditRef,
        )
        is HostOutcome.RecoveryRequired -> DomainOutcome.RecoveryRequired(
            idempotencyKey = host.idempotencyKey,
            code = host.code,
            auditRef = host.auditRef,
        )
    }

    fun toRecoveryStatus(dto: OperationLookupDto): RecoveryStatus {
        val statusLower = dto.status.trim().lowercase()
        val state = when (statusLower) {
            "completed" -> RecoveryState.RESOLVED_SUCCEEDED
            "pending" -> RecoveryState.PENDING_RETRY
            "refused" -> {
                val c = dto.code?.trim()?.uppercase().orEmpty()
                if (c in CONFLICT_CODES) RecoveryState.RESOLVED_CONFLICT else RecoveryState.RESOLVED_REFUSED
            }
            "failed" -> RecoveryState.MANUAL_REVIEW_REQUIRED
            else -> RecoveryState.MANUAL_REVIEW_REQUIRED
        }
        return RecoveryStatus(
            idempotencyKey = dto.id,
            state = state,
            gatewayOperationStatus = dto.status,
            auditRef = dto.auditRef,
            headSha = dto.headSha,
            errorCode = dto.code,
            detail = when (state) {
                RecoveryState.RESOLVED_SUCCEEDED -> "Gateway confirmed completion."
                RecoveryState.PENDING_RETRY -> "Operation is still pending on gateway."
                RecoveryState.RESOLVED_REFUSED -> "Gateway refused operation (${dto.code ?: "REFUSED"})."
                RecoveryState.RESOLVED_CONFLICT -> "Gateway conflict (${dto.code ?: "CONFLICT"})."
                else -> "Lookup state '${dto.status}' requires manual inspection."
            },
        )
    }

    private val CONFLICT_CODES = setOf(
        "HEAD_DRIFT",
        "HEAD_MOVED",
        "NONCE_REPLAYED",
        "SUBMISSION_IMMUTABLE",
        "ANCESTRY_FAIL",
        "RECONCILE_REQUIRED",
        "IDEMPOTENCY_KEY_REUSED",
    )
}
