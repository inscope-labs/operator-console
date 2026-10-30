package com.inscopelabs.buildengine.core.crypto

/**
 * Owns the device signing keypair.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §4: the private key never leaves the Secure Element. This class must
 * never expose the private key material, only signatures and the public key.
 */
class DeviceKeyManager {

    /** Creates the key if absent; returns the alias in use. */
    fun ensureDeviceKey(spec: DeviceKeySpec): String = TODO("SCAFFOLD: create or load the Keystore keypair.")

    /** Base64url-encoded public key for gateway enrolment. */
    fun publicKeyBase64(alias: String): String = TODO("SCAFFOLD: read the public key.")

    /** Removes the key. Used by rotation and by device-loss revocation. */
    fun deleteDeviceKey(alias: String): Unit = TODO("SCAFFOLD: delete the Keystore key.")
}
