package com.inscopelabs.buildengine.core.security

/**
 * The certificate pin set for the gateway host.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: pinning prevents a proxy or a mis-pointed DNS from becoming a
 * credential-capture point. See docs/adr/0003-mtls-to-gateway.md.
 */
class CertificatePinning {

    fun pinsFor(host: String): List<String> = TODO("SCAFFOLD: return the configured pin set.")

    fun isPinned(host: String): Boolean = TODO("SCAFFOLD: report whether the host is pinned.")
}
