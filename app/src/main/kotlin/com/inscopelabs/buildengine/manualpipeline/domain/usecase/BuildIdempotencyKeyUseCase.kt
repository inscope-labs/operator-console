package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import java.security.SecureRandom
import javax.inject.Inject

/**
 * Generates and validates client-side 32-hex idempotency keys (ADR 0013).
 * Strictly distinct from capability nonces and never stored alongside signatures.
 */
class BuildIdempotencyKeyUseCase @Inject constructor() {

    private val random = SecureRandom()

    operator fun invoke(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xFF) }
    }

    fun isValid(key: String?): Boolean =
        key != null && KEY_REGEX.matches(key)

    companion object {
        private val KEY_REGEX = Regex("^[0-9a-f]{32}$")
    }
}
