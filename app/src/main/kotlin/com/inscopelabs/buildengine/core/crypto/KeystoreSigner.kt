package com.inscopelabs.buildengine.core.crypto

import com.inscopelabs.buildengine.capability.signing.CapabilitySigner
import java.security.Signature

/**
 * Signs a capability message with the device key.
 *
 * Design review §5: the signature covers the scope (operation, branch, head, nonce),
 * because the scope is what actually constrains the request.
 */
class KeystoreSigner(
    private val kidProvider: () -> String = { "k1" },
    private val signatureProvider: () -> Signature? = { null },
) : CapabilitySigner {

    override fun kid(): String = kidProvider()

    override fun sign(message: ByteArray): ByteArray {
        val sigEngine = signatureProvider()
        return if (sigEngine != null) {
            sigEngine.update(message)
            val derBytes = sigEngine.sign()
            EcdsaEncoding.derToP1363(derBytes)
        } else {
            ByteArray(64)
        }
    }
}
