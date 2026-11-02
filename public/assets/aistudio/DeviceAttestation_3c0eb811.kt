package com.inscopelabs.buildengine.core.security

import android.security.keystore.KeyInfo
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64

/**
 * Reads the Android Keystore key-attestation certificate chain.
 *
 * requestAttestation remains deferred exactly as required by the Stage 4.1 manifest.
 * This class never fabricates an attestation or substitutes a self-asserted device
 * property for a certificate chain.
 */
class DeviceAttestation(
    private val deviceKeyManager: DeviceKeyManager,
) {
    /** Deferred Play Integrity / server challenge request. Fail closed, never fake it. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun requestAttestation(nonce: String): ByteArray =
        throw UnsupportedOperationException(
            "Play Integrity attestation is deferred (D-T4); no attestation was produced"
        )

    /**
     * Returns DER certificates in the alias's Android Keystore attestation chain,
     * each standard-Base64 encoded as expected by the existing gateway DTO.
     */
    fun keyAttestationChain(alias: String): List<String> {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val chain = store.getCertificateChain(alias)
            ?: throw GeneralSecurityException("No key-attestation certificate chain is available")
        if (chain.isEmpty()) throw GeneralSecurityException("Key-attestation chain is empty")
        return chain.map { Base64.getEncoder().encodeToString(it.encoded) }
    }

    fun hasKeyAttestation(alias: String): Boolean = try {
        keyAttestationChain(alias).isNotEmpty()
    } catch (_: Exception) {
        false
    }
}
