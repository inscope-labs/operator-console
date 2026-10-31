package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The gateway's acknowledgement of a privileged operation.
 *
 * docs/gateway-contract.md §3.8–§3.12 — the acknowledgement body is the same for
 * every privileged operation.
 *
 * An acknowledgement is not a success: it says the gateway accepted the operation
 * and carried it out. `accepted` says whether it was taken, `outcome` carries the
 * result where the operation has one (`MERGED:pr-142` for a reconcile), and
 * `auditRef` points at the audit record the gateway wrote — the durable trail lives
 * in the private consumer repository, never on the device (design review §8).
 */
@Serializable
data class GatewayAckDto(
    val accepted: Boolean = false,
    val operation: String = "",
    val branch: String = "",
    val headSha: String = "",
    val outcome: String = "",
    val auditRef: String = "",
)
