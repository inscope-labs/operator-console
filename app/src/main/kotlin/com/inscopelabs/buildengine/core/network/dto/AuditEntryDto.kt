package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Audit journal entry returned by `GET /audit` and `GET /audit/{ref}` (contract §3.20).
 *
 * All optional/nullable fields default so gateway records that omit or null-out
 * non-applicable fields (for example `task` on an engine-level halt) decode cleanly.
 */
@Serializable
data class AuditEntryDto(
    val auditRef: String = "",
    val timestamp: String = "",
    val operation: String = "",
    val plan: String? = null,
    val task: String? = null,
    val branch: String? = null,
    val headSha: String? = null,
    val kid: String? = null,
    val authTier: String? = null,
    val outcome: String? = null,
    val code: String? = null,
)
