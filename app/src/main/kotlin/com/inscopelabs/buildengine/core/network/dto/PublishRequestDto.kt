package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A publish request as it goes on the wire.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the capability travels with the request and names the branch,
 * the head SHA and the operation.
 */
@Serializable
data class PublishRequestDto(
    val plan: String = "",
    val task: String = "",
    val branch: String = "",
    val head: String = "",
    val capability: String = "",
)
