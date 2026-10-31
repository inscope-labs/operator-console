package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The result of validating a plan's definitions.
 *
 * docs/gateway-contract.md §3.4 — `POST /plans/{id}/validate`.
 *
 * A read-only check: it changes nothing, and it needs no capability.
 */
@Serializable
data class PlanValidationDto(
    val valid: Boolean = false,
    val planId: String = "",
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)
