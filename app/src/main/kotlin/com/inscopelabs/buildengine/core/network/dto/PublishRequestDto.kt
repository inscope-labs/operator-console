package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A publish request as it goes on the wire.
 *
 * docs/gateway-contract.md §3.8 — `POST /tasks/{id}/publish`.
 *
 * Design review §5: the capability travels with the request and names the branch,
 * the head SHA and the operation.
 *
 * The branch and the head are repeated here even though the capability already
 * carries them, and that repetition is the point: the gateway checks the request
 * against the capability, so a request that names one branch while the capability
 * names another is a `BRANCH_MISMATCH` and is refused. If the request carried only
 * the capability, there would be nothing to disagree with.
 */
@Serializable
data class PublishRequestDto(
    val plan: String = "",
    val task: String = "",
    val branch: String = "",
    val head: String = "",
    val capability: String = "",
)
