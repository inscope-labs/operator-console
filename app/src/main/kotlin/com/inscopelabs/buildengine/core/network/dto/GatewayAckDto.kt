package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The gateway's acknowledgement of a privileged operation.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@Serializable
data class GatewayAckDto(
    val accepted: Boolean = false,
    val operation: String = "",
    val branch: String = "",
    val headSha: String = "",
    val auditRef: String = "",
)
