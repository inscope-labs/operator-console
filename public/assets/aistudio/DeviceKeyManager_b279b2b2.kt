package com.inscopelabs.buildengine.core.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.annotation.RequiresApi
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Owns the Android Keystore keypair. Private key material is never returned.
 *
 * Methods are synchronous by design because Android Keystore operations are short;
 * callers performing them from UI must use Dispatchers.IO.
 */
class DeviceKeyManager(context: Context) {
    private val appContext = context.applicationContext

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    fun contains(alias: String): Boolean = keyStore().containsAlias(requireAlias(alias))

    /**
     * Creates a key if absent. Existing aliases are never silently overwritten.
     * StrongBox fallback occurs only for a StrongBox-specific availability failure.
     */
    @Synchronized
    fun ensureDeviceKey(spec: DeviceKeySpec): String {
        val alias = requireAlias(spec.alias)
        val store = keyStore()
        if (store.containsAlias(alias)) {
            val privateKey = store.getKey(alias, null) as? PrivateKey
                ?: throw GeneralSecurityException("Keystore alias exists but is not a private key")
            val publicKey = store.getCertificate(alias)?.publicKey
                ?: throw GeneralSecurityException("Keystore key has no certificate")
            validateExistingKey(privateKey, publicKey)
            return alias
        }

        if (spec.preferStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            StrongBoxSupport(appContext).isStrongBoxAvailable()
        ) {
            try {
                generate(spec, strongBox = true)
                return alias
            } catch (_: StrongBoxUnavailableException) {
                // The platform explicitly says StrongBox is unavailable; TEE fallback is permitted.
            }
        }
        generate(spec, strongBox = false)
        return alias
    }

    /** Returns the SubjectPublicKeyInfo DER public key as base64url without padding. */
    @Synchronized
    fun publicKeyBase64(alias: String): String = Base64Url.encode(publicKey(alias).encoded)

    fun publicKey(alias: String): PublicKey {
        val cert = keyStore().getCertificate(requireAlias(alias))
            ?: throw GeneralSecurityException("No enrolled public key exists for alias")
        return cert.publicKey
    }

    /**
     * Returns an initialized ECDSA Signature bound to this Keystore private key.
     *
     * The Android Keystore enforces per-use authentication. Stage 4.2 must pass this
     * Signature as BiometricPrompt.CryptoObject and only call sign after success.
     */
    fun prepareSignature(alias: String): Signature {
        val privateKey = keyStore().getKey(requireAlias(alias), null) as? PrivateKey
            ?: throw GeneralSecurityException("No private key exists for alias")
        return Signature.getInstance(SIGNATURE_ALGORITHM).apply { initSign(privateKey) }
    }

    @Synchronized
    fun deleteDeviceKey(alias: String) {
        val safeAlias = requireAlias(alias)
        val store = keyStore()
        if (store.containsAlias(safeAlias)) store.deleteEntry(safeAlias)
    }

    private fun generate(spec: DeviceKeySpec, strongBox: Boolean) {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            spec.alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(spec.curve))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(-1)
            .setInvalidatedByBiometricEnrollment(true)

        spec.attestationChallenge?.let(builder::setAttestationChallenge)

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        generator.initialize(builder.build())
        generator.generateKeyPair()
    }

    private fun validateExistingKey(privateKey: PrivateKey, publicKey: PublicKey) {
        if (privateKey.algorithm != KeyProperties.KEY_ALGORITHM_EC ||
            publicKey.algorithm != KeyProperties.KEY_ALGORITHM_EC
        ) {
            throw GeneralSecurityException("Existing alias does not contain an EC signing key")
        }
        val info = StrongBoxSupport(appContext).keyInfo(privateKey)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !info.isUserAuthenticationRequired) {
            throw GeneralSecurityException("Existing key does not require user authentication")
        }
    }

    private fun requireAlias(alias: String): String {
        require(alias.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid Android Keystore alias" }
        return alias
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
