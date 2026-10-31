package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A reconcile request as it goes on the wire.
 *
 * docs/gateway-contract.md §3.9 — `POST /tasks/{id}/reconcile`.
 *
 * A reconcile merges a reviewed task branch, so it names the submission it is
 * reconciling as well as the branch, the head and the capability.
 */
@Serializable
data class ReconcileRequestDto(
    val plan: String = "",
    val task: String = "",
    val branch: String = "",
    val head: String = "",
    val submissionId: String = "",
    val capability: String = "",
)
