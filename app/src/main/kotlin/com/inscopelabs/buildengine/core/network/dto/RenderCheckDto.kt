package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Whether a plan's generated views are current.
 *
 * docs/gateway-contract.md §3.5 — `GET /plans/{id}/render-check`.
 *
 * A read-only check: the plan's rendered output exists and matches what the plan
 * says it should be.
 */
@Serializable
data class RenderCheckDto(
    val ok: Boolean = false,
    val planId: String = "",
    val renderIssues: List<String> = emptyList(),
)
