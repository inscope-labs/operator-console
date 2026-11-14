package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for `POST /plans/{id}/decide` strictly matching `docs/schemas/decide.schema.json`
 * (`additionalProperties: false`) and contract revision 1.1 §3.18.
 *
 * Note (PR3-003 / PR3-025): the UI fields required by RELEASE-PLAN T3-404
 * (`attribution`, `rationale`, `scope`, `evidence`) and a `headSha`/`branch` acknowledgement
 * for live-head verification (T3-302) are not defined in `decide.schema.json`. Do not add
 * unapproved fields here.
 */
@Serializable
data class DecideRequestDto(
    val plan: String = "",
    val task: String? = null,
    val branch: String? = null,
    val decision: String = "",
    val notes: String? = null,
    val idempotencyKey: String = "",
    val capability: String = "",
)

@Serializable
data class DecideAckDto(
    val accepted: Boolean = false,
    val operation: String = "",
    val plan: String = "",
    val decision: String = "",
    val auditRef: String = "",
)
