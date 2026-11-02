package com.inscopelabs.buildengine.core.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Owns the device signing keypair in the Android Keystore.
 *
 * Design review §4: the private key never leaves the Secure Element. This class must
 * never expose the private key material, only signatures and the public key.
 */
open class DeviceKeyManager(
    val context: Context? = null,
    private val keyStoreProvider: () -> KeyStore = { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } },
) {
    private val keyStore: KeyStore
        get() = keyStoreProvider()

    /** Creates the key if absent; returns the alias in use. */
    open fun ensureDeviceKey(spec: DeviceKeySpec): String {
        val store = keyStore
        if (store.containsAlias(spec.alias)) {
            return spec.alias
        }
        generateKeyPair(spec)
        return spec.alias
    }

    private fun generateKeyPair(spec: DeviceKeySpec) {
        val purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        if (spec.preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateWithSpec(createBuilder(spec, purposes).setIsStrongBoxBacked(true).build())
                return
            } catch (_: StrongBoxUnavailableException) {
                // StrongBox not available on this device; fall back to normal hardware TEE Keystore
            } catch (e: Exception) {
                val cause = e.cause
                if (cause is StrongBoxUnavailableException || e.message?.contains("StrongBox", ignoreCase = true) == true) {
                    // Fall back
                } else {
                    throw e
                }
            }
        }
        generateWithSpec(createBuilder(spec, purposes).build())
    }

    private fun createBuilder(spec: DeviceKeySpec, purposes: Int): KeyGenParameterSpec.Builder {
        val builder = KeyGenParameterSpec.Builder(spec.alias, purposes)
            .setAlgorithmParameterSpec(ECGenParameterSpec(spec.curve))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(spec.userAuthenticationRequired)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
            spec.attestationChallenge?.let { challenge ->
                builder.setAttestationChallenge(challenge)
            }
        }
        return builder
    }

    private fun generateWithSpec(keyGenSpec: KeyGenParameterSpec) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        kpg.initialize(keyGenSpec)
        kpg.generateKeyPair()
    }

    /** Base64url-encoded public key for gateway enrolment. */
    open fun publicKeyBase64(alias: String): String {
        val publicKey = getPublicKey(alias)
            ?: throw IllegalStateException("Device key '$alias' not found in Keystore")
        return Base64Url.encode(publicKey.encoded)
    }

    open fun getPublicKey(alias: String): PublicKey? {
        val store = keyStore
        return store.getCertificate(alias)?.publicKey
    }

    open fun getPrivateKey(alias: String): PrivateKey? {
        val store = keyStore
        return store.getKey(alias, null) as? PrivateKey
    }

    open fun hasDeviceKey(alias: String): Boolean = keyStore.containsAlias(alias)

    /** Removes the key. Used by rotation and by device-loss revocation. */
    open fun deleteDeviceKey(alias: String) {
        val store = keyStore
        if (store.containsAlias(alias)) {
            store.deleteEntry(alias)
        }
    }

    companion object {
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
    }
}
