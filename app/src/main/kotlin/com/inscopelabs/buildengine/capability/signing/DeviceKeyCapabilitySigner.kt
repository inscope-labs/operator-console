package com.inscopelabs.buildengine.capability.signing

import com.inscopelabs.buildengine.core.crypto.KeystoreSigner

/**
 * Model B — the console signs with its device key, the gateway verifies.
 *
 * Design review §5: this is the recommended direction because it makes the capability
 * a record of a human decision rather than a pre-issued permission.
 */
class DeviceKeyCapabilitySigner(
    private val keystoreSigner: KeystoreSigner = KeystoreSigner(),
) : CapabilitySigner {

    override fun kid(): String = keystoreSigner.kid()

    override fun sign(message: ByteArray): ByteArray = keystoreSigner.sign(message)
}
