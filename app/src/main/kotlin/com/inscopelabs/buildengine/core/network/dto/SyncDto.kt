package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A sync request and its answer.
 *
 * docs/gateway-contract.md §3.13 — `POST /sync`.
 *
 * A read-only fast-forward: it needs the bearer credential but no capability,
 * because it writes no branch.
 */
@Serializable
data class SyncRequestDto(
    val branch: String = "",
)

@Serializable
data class SyncStatusDto(
    val status: String = "",
    val headSha: String = "",
    val inSync: Boolean = false,
)
