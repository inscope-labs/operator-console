package com.inscopelabs.buildengine.core.crypto

import android.security.keystore.KeyProperties

/**
 * Immutable policy for one non-exportable device signing key.
 *
 * The defaults are deliberately conservative: EC P-256, SHA-256, per-use user
 * authentication and invalidation when biometric enrolment changes.
 */
data class DeviceKeySpec(
    val alias: String,
    val keyAlgorithm: String = KeyProperties.KEY_ALGORITHM_EC,
    val curve: String = "secp256r1",
    val userAuthenticationRequired: Boolean = true,
    val preferStrongBox: Boolean = true,
    /** Challenge embedded in the Android key-attestation extension at key creation. */
    val attestationChallenge: ByteArray? = null,
) {
    init {
        require(alias.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid Android Keystore alias" }
        require(keyAlgorithm == KeyProperties.KEY_ALGORITHM_EC) { "Only EC keys are supported by ctxop-v1" }
        require(curve == "secp256r1") { "Only NIST P-256 is supported by this signer" }
        require(userAuthenticationRequired) { "Operator signing keys must require user authentication" }
        require(attestationChallenge == null || attestationChallenge.size in 16..128) {
            "Attestation challenge must be 16–128 bytes"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is DeviceKeySpec &&
            alias == other.alias &&
            keyAlgorithm == other.keyAlgorithm &&
            curve == other.curve &&
            userAuthenticationRequired == other.userAuthenticationRequired &&
            preferStrongBox == other.preferStrongBox &&
            ((attestationChallenge == null && other.attestationChallenge == null) ||
                (attestationChallenge != null && other.attestationChallenge != null &&
                    attestationChallenge.contentEquals(other.attestationChallenge)))

    override fun hashCode(): Int =
        31 * listOf(alias, keyAlgorithm, curve, userAuthenticationRequired, preferStrongBox).hashCode() +
            (attestationChallenge?.contentHashCode() ?: 0)
}
