package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The status of one plan.
 *
 * docs/gateway-contract.md §3.3 — `GET /plans/{id}/status`.
 *
 * `halted` is the engine's halt marker and `openBranches` are the branches the
 * plan currently has open. Together they are what the operator needs to see before
 * approving a write: a halted engine and a drifted branch are the two states in
 * which a mint would be refused.
 */
@Serializable
data class PlanStatusDto(
    val id: String = "",
    val status: String = "",
    val halted: Boolean = false,
    val openBranches: List<String> = emptyList(),
    val blockedTasks: List<String> = emptyList(),
    val currentMilestone: String = "",
)
