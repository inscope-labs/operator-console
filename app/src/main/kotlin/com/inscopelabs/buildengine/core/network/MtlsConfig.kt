package com.inscopelabs.buildengine.core.network

/**
 * Transport configuration for the gateway connection.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: the channel must be mutually authenticated, not merely TLS.
 */
data class MtlsConfig(
    val host: String,
    val pins: List<String> = emptyList(),
    val clientCertificateAlias: String? = null,
    val attestationToken: String? = null,
)
