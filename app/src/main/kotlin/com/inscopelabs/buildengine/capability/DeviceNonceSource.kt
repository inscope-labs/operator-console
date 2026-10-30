package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource

/**
 * The device's [NonceSource], backed by the platform CSPRNG.
 *
 * Design review §5: the gateway stores the nonce until expiry and refuses one it has
 * already seen. The nonce is therefore the console's contribution to replay
 * protection, and it must come from a cryptographically secure source — a nonce
 * drawn from a predictable generator would let an attacker mint a second capability
 * for the same branch and head.
 *
 * This class lives in the `capability` package rather than in `core.crypto` so that
 * `core` stays unaware of capabilities: `core` provides the entropy, `capability`
 * decides what to spend it on.
 */
class DeviceNonceSource(
    private val secureRandomSource: SecureRandomSource,
) : NonceSource {
    override fun newNonce(): String = secureRandomSource.nextNonce()
}
