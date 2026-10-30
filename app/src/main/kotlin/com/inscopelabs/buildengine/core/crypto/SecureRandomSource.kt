package com.inscopelabs.buildengine.core.crypto

import java.security.SecureRandom

/**
 * Cryptographically secure source of nonces.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the nonce is a one-time value, stored by the gateway until expiry.
 */
class SecureRandomSource {

    private val random: SecureRandom = SecureRandom()

    fun nextNonce(): String = TODO("SCAFFOLD: produce a random nonce from the CSPRNG.")

    fun nextBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
}
