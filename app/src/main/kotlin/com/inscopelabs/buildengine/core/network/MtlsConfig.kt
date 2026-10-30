package com.inscopelabs.buildengine.core.network

/**
 * Transport configuration for the gateway connection — **deferred, and unused**.
 *
 * Design review §13 specified a mutually authenticated channel.
 * docs/adr/0006-mtls-deferred.md supersedes that for this application: the console
 * talks to the gateway over **standard HTTPS (TLS 1.3) with host verification**, and
 * its identity on the gateway is established instead by
 *
 * 1. the `Authorization: Bearer <operator_credential>` header — gateway-scoped, and
 *    holding no repository authority, and
 * 2. the signed `ctxop-v1` capability on every privileged operation — bound to the
 *    device's non-exportable Keystore key.
 *
 * ADR 0006 records why: a key with `setUserAuthenticationRequired(true)` cannot
 * serve a TLS handshake without a biometric prompt on every connection, and
 * Cloudflare's default `*.workers.dev` endpoints do not terminate client
 * certificates at all.
 *
 * This type is kept because the decision is a deferral and not a reversal — it can
 * be upgraded to full mTLS on an enterprise domain — and because a scaffold must
 * not fail a build over a missing deployment secret. It configures nothing, and
 * nothing reads it.
 */
data class MtlsConfig(
    val host: String,
    val pins: List<String> = emptyList(),
    val clientCertificateAlias: String? = null,
    val attestationToken: String? = null,
)
