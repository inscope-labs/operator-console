package com.inscopelabs.buildengine.core.crypto

import java.security.GeneralSecurityException
import java.security.Signature

/**
 * ECDSA-P256 signer backed by Android Keystore.
 *
 * This class intentionally has no zero-signature or software-key fallback. The
 * caller must provide a Signature authorized by BiometricPrompt.CryptoObject. Stage
 * 4.2 owns that interaction; this class only performs the cryptographic operation.
 */
class KeystoreSigner(
    private val deviceKeyManager: DeviceKeyManager? = null,
    private val kidProvider: () -> String = { throw IllegalStateException("No active enrolled key id") },
    private val signatureProvider: () -> Signature? = { null },
) {
    fun kid(): String = kidProvider()

    /** Signs with a CryptoObject-authorized Signature supplied by the biometric gate. */
    fun sign(message: ByteArray): ByteArray {
        require(message.isNotEmpty()) { "Cannot sign an empty capability message" }
        val signature = signatureProvider()
            ?: throw GeneralSecurityException(
                "No biometric-authorized Keystore Signature is available; capability signing is fail-closed"
            )
        return signature.run {
            update(message)
            EcdsaEncoding.derToP1363(sign())
        }
    }

    /** Prepares the Signature that Stage 4.2 must wrap in BiometricPrompt.CryptoObject. */
    fun prepare(alias: String): Signature =
        (deviceKeyManager ?: throw IllegalStateException("DeviceKeyManager is not configured"))
            .prepareSignature(alias)
}
