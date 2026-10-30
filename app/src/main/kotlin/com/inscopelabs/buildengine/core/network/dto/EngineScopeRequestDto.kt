package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A request against the engine branch rather than a task branch.
 *
 * docs/gateway-contract.md §3.11–§3.12 — `POST /halt` and `POST /resume`.
 *
 * `halt` and `resume` are repository-wide: they affect every plan, and the
 * capability that authorises them is scoped to the `engine` branch rather than to a
 * task. That is why they cannot reuse the scope of the publish the operator was
 * just looking at (docs/capability-format.md §3).
 *
 * `reason` is recorded with the halt so that the audit trail says why the engine
 * stopped.
 */
@Serializable
data class EngineScopeRequestDto(
    val branch: String = "",
    val head: String = "",
    val reason: String = "",
    val capability: String = "",
)
