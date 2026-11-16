package com.inscopelabs.buildengine.manualpipeline.util

import java.security.SecureRandom

/**
 * 32-hex lowercase idempotency key helper aligned with ADR 0013 & gateway-contract §1.6.
 * Never derived from any capability nonce, signature, or bearer credential.
 */
object IdempotencyKey {

    private const val KEY_BYTES = 16
    private val KEY_REGEX = Regex("^[0-9a-f]{32}$")
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(KEY_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
    }

    fun isValid(candidate: String?): Boolean =
        candidate != null && KEY_REGEX.matches(candidate)
}
