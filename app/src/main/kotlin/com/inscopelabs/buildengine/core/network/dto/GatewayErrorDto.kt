package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The gateway's error body.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@Serializable
data class GatewayErrorDto(
    val code: String = "",
    val message: String = "",
)
