package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for `POST /plans/{id}/status` strictly matching
 * `docs/schemas/plan-status-transition.schema.json` (`additionalProperties: false`)
 * and contract revision 1.1 §3.17.
 */
@Serializable
data class PlanStatusTransitionRequestDto(
    val plan: String = "",
    val task: String = "",
    val branch: String = "",
    val expectedHead: String = "",
    val targetStatus: String = "",
    val idempotencyKey: String = "",
    val capability: String = "",
)

@Serializable
data class PlanStatusTransitionAckDto(
    val accepted: Boolean = false,
    val operation: String = "",
    val plan: String = "",
    val task: String = "",
    val newStatus: String = "",
    val headSha: String = "",
    val auditRef: String = "",
)
