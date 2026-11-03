package com.inscopelabs.buildengine.core.crypto

import com.inscopelabs.buildengine.capability.signing.CapabilitySigner
import java.security.GeneralSecurityException
import java.security.Signature

/**
 * Signs a capability message with the device key.
 *
 * Design review §5: the signature covers the scope (operation, branch, head, nonce),
 * because the scope is what actually constrains the request.
 *
 * Fails closed if no signature provider or initialized Signature object is available.
 * Returns raw r||s format (IEEE P1363, 64 bytes for secp256r1) as required by the gateway.
 */
class KeystoreSigner(
    private val kidProvider: () -> String = { "k1" },
    private val signatureProvider: () -> Signature? = { null },
) : CapabilitySigner {

    constructor(deviceKeyManager: DeviceKeyManager) : this(
        kidProvider = { "k1" },
        signatureProvider = { null },
    )

    constructor(kid: String, authorizedSignature: Signature) : this(
        kidProvider = { kid },
        signatureProvider = { authorizedSignature },
    )

    constructor(authorizedSignature: Signature) : this(
        kidProvider = { "k1" },
        signatureProvider = { authorizedSignature },
    )

    override fun kid(): String = kidProvider()

    override fun sign(message: ByteArray): ByteArray {
        val sigEngine = signatureProvider()
            ?: throw GeneralSecurityException("No authorized Signature engine available for device signing")
        sigEngine.update(message)
        val derBytes = sigEngine.sign()
        return EcdsaEncoding.derToP1363(derBytes)
    }
}
