package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Device key revocation, as it goes on the wire.
 *
 * docs/gateway-contract.md §3.21 — `POST /admin/revoke` (BD-1.10).
 *
 * The operator bearer credential authorizes the call; [confirmation] is the typed
 * confirmation gesture (`REVOKE <kid>`), and the gateway records the revocation in
 * its audit journal.
 */
@Serializable
data class RevokeKeyRequestDto(
    val kid: String = "",
    val reason: String = "",
    val confirmation: String = "",
)

@Serializable
data class RevokeKeyResponseDto(
    val revoked: Boolean = false,
    val kid: String = "",
    val revokedAt: Long = 0L,
)
