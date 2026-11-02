package com.inscopelabs.buildengine.core.crypto

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
        if (!attestationChallenge.contentEquals(other.attestationChallenge)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + keyAlgorithm.hashCode()
        result = 31 * result + curve.hashCode()
        result = 31 * result + userAuthenticationRequired.hashCode()
        result = 31 * result + preferStrongBox.hashCode()
        result = 31 * result + (attestationChallenge?.contentHashCode() ?: 0)
        return result
    }
}
