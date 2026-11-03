package com.inscopelabs.buildengine.capability.signing

import com.inscopelabs.buildengine.core.crypto.KeystoreSigner
import java.security.Signature

/**
 * Model B — the console signs with its device key, the gateway verifies.
 *
 * Design review §5: this is the recommended direction because it makes the capability
 * a record of a human decision rather than a pre-issued permission.
 */
class DeviceKeyCapabilitySigner(
    private val keystoreSigner: KeystoreSigner = KeystoreSigner(),
) : CapabilitySigner {

    constructor(kid: String, authorizedSignature: Signature) : this(
        KeystoreSigner(kid, authorizedSignature)
    )

    constructor(authorizedSignature: Signature) : this(
        KeystoreSigner(authorizedSignature)
    )

    override fun kid(): String = keystoreSigner.kid()

    override fun sign(message: ByteArray): ByteArray = keystoreSigner.sign(message)
}
