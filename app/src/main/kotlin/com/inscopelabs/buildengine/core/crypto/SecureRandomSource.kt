package com.inscopelabs.buildengine.core.crypto

import java.security.SecureRandom

/**
 * Cryptographically secure source of nonces.
 *
 * Design review §5: the nonce is a one-time value, stored by the gateway until expiry.
 *
 * The nonce is what makes a capability non-replayable, so it is drawn from a CSPRNG
 * and never from [kotlin.random.Random]. It is rendered as lowercase hex rather than
 * base64url because it travels inside the LF-separated capability message, where a
 * hex body is unambiguous and needs no escaping.
 *
 * The source takes its [SecureRandom] as a constructor parameter so a test can drive
 * it deterministically; the default is the platform CSPRNG, which is what production
 * uses.
 */
class SecureRandomSource(
    private val random: SecureRandom = SecureRandom(),
) {

    /**
     * A fresh nonce: [NONCE_BYTES] CSPRNG bytes, lowercase hex.
     */
    fun nextNonce(): String = nextBytes(NONCE_BYTES).toHex()

    /**
     * [size] bytes from the CSPRNG.
     */
    fun nextBytes(size: Int): ByteArray {
        require(size >= 0) { "size must not be negative" }
        return ByteArray(size).also(random::nextBytes)
    }

    private fun ByteArray.toHex(): String {
        val hex = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            hex.append(HEX_DIGITS[value ushr 4])
            hex.append(HEX_DIGITS[value and 0x0F])
        }
        return hex.toString()
    }

    companion object {
        /**
         * The number of random bytes behind a nonce.
         *
         * 16 bytes is 128 bits, which is the usual floor for a value that must not
         * repeat within the capability's five-minute lifetime. The nonce format itself
         * is not constrained by the wire contract — `docs/capability-format.md` §2 puts
         * no length rule on the field, only on `kid`, `branch` and `head` — so the
         * entropy is spent where the contract does not pin it down.
         */
        const val NONCE_BYTES: Int = 16

        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
