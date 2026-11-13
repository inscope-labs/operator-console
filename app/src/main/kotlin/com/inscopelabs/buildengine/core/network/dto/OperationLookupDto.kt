package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Response body from `GET /operations/{id}` (docs/gateway-contract.md §3.19,
 * docs/schemas/operation-lookup.schema.json).
 */
@Serializable
data class OperationLookupDto(
    val id: String = "",
    val operation: String = "",
    val status: String = "",
    val branch: String? = null,
    val headSha: String? = null,
    val auditRef: String? = null,
    val timestamp: Long = 0L,
    val code: String? = null,
)
