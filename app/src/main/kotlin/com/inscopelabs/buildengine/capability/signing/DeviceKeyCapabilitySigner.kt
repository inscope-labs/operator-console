package com.inscopelabs.buildengine.capability.signing

/**
 * Model B — the console signs with its device key, the gateway verifies.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: this is the recommended direction because it makes the capability
 * a record of a human decision rather than a pre-issued permission.
 */
class DeviceKeyCapabilitySigner : CapabilitySigner {

    override fun kid(): String = TODO("SCAFFOLD: return the enrolled key selector.")

    override fun sign(message: ByteArray): ByteArray =
        TODO("SCAFFOLD: sign with the non-exportable device key.")
}
