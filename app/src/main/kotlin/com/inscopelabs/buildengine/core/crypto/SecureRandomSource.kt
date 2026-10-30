package com.inscopelabs.buildengine.core.crypto

import java.security.SecureRandom
import java.util.Random

/**
 * Cryptographically secure source of nonces.
 *
 * Design review §5: the nonce is a one-time value, stored by the gateway until expiry.
 */
class SecureRandomSource(
    private val random: Random = SecureRandom(),
) {

    fun nextBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun nextNonce(byteCount: Int = NONCE_BYTES): String {
        val bytes = nextBytes(byteCount)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val NONCE_BYTES: Int = 16
    }
}
