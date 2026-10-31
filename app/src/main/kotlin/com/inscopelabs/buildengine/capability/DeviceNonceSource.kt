package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Every nonce handed out by this instance. A nonce is only meaningful for one capability
     * TTL, so the set is small; it is kept for the instance lifetime for simplicity.
     */
    private val issued = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns a nonce this instance has never returned before. A repeat from the random
     * source is retried; if the source keeps repeating, it is broken and the call fails
     * rather than ever issuing a duplicate.
     */
    override fun newNonce(): String {
        repeat(MAX_ATTEMPTS) {
            val candidate = secureRandomSource.nextNonce()
            if (issued.add(candidate)) return candidate
        }
        throw IllegalStateException("Random source produced only repeated nonces; refusing to issue a duplicate")
    }

    private companion object {
        const val MAX_ATTEMPTS = 8
    }
}
