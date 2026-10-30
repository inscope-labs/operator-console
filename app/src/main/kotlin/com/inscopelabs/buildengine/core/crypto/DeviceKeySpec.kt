package com.inscopelabs.buildengine.core.crypto

/**
 * Parameters describing the device signing key.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
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
)
