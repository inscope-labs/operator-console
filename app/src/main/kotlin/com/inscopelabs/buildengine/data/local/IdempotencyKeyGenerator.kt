package com.inscopelabs.buildengine.data.local

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource

/**
 * Generates client-side operation idempotency keys (ADR 0013, docs/gateway-contract.md §1.6).
 *
 * Each key is 128 random bits from [SecureRandomSource], encoded as 32 lowercase hexadecimal
 * characters (`^[0-9a-f]{32}$`). It is a random lookup label, strictly distinct from and never
 * derived from any capability nonce, signature, or capability string.
 */
class IdempotencyKeyGenerator(
    private val randomSource: SecureRandomSource = SecureRandomSource(),
) {

    fun generate(): String {
        val bytes = randomSource.nextBytes(KEY_BYTE_COUNT)
        require(bytes.size == KEY_BYTE_COUNT) {
            "Expected $KEY_BYTE_COUNT bytes for idempotency key, got ${bytes.size}"
        }
        val key = bytes.joinToString(separator = "") { "%02x".format(it) }
        check(isValid(key)) { "Generated idempotency key does not match contract pattern" }
        return key
    }

    companion object {
        const val KEY_BYTE_COUNT: Int = 16
        private val KEY_REGEX = Regex("^[0-9a-f]{32}$")

        fun isValid(key: String?): Boolean = key != null && KEY_REGEX.matches(key)
    }
}
