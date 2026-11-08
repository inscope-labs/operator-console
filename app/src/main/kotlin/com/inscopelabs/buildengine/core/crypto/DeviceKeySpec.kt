package com.inscopelabs.buildengine.core.crypto

import com.inscopelabs.buildengine.core.security.AuthTier

/**
 * Parameters describing the device signing key.
 *
 * Design review §4, §13: the key is non-exportable and lives in the Keystore.
 * [userAuthenticationRequired] is the control that makes a capability impossible to
 * mint without a successful operator confirmation.
 */
data class DeviceKeySpec(
    val alias: String,
    val keyAlgorithm: String = "EC",
    val curve: String = "secp256r1",
    val userAuthenticationRequired: Boolean = true,
    val preferStrongBox: Boolean = true,
    val attestationChallenge: ByteArray? = null,
    val authTier: AuthTier = AuthTier.HARDWARE_CONFIRM,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceKeySpec

        if (alias != other.alias) return false
        if (keyAlgorithm != other.keyAlgorithm) return false
        if (curve != other.curve) return false
        if (userAuthenticationRequired != other.userAuthenticationRequired) return false
        if (preferStrongBox != other.preferStrongBox) return false
        if (authTier != other.authTier) return false
        if (!attestationChallenge.contentEquals(other.attestationChallenge)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + keyAlgorithm.hashCode()
        result = 31 * result + curve.hashCode()
        result = 31 * result + userAuthenticationRequired.hashCode()
        result = 31 * result + preferStrongBox.hashCode()
        result = 31 * result + authTier.hashCode()
        result = 31 * result + (attestationChallenge?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Derives a canonical Keystore alias namespaced by environment and host (`op_<env>_<host>`).
         *
         * Per ADR 0007 / B.4 item 1 and T2-204:
         * Isolation must come from a separate Keystore key per environment and host so a capability
         * captured on staging is never valid or signable on production.
         */
        fun deriveAlias(environment: String, host: String): String {
            val cleanEnv = environment.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
            require(cleanEnv.isNotEmpty()) { "Environment must not be blank" }

            val cleanHost = host.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore('/')
                .substringBefore(':')
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "_")
                .trim('_')

            val raw = if (cleanHost.isEmpty()) "op_${cleanEnv}" else "op_${cleanEnv}_${cleanHost}"
            // Android Keystore alias max length is 64 characters per Keystore & DeviceEnrollmentStore validation
            return if (raw.length > 64) raw.take(64).trimEnd('_') else raw
        }
    }
}
