package com.inscopelabs.buildengine.core.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.inscopelabs.buildengine.core.security.AuthTier
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.UnrecoverableKeyException
import java.security.spec.ECGenParameterSpec

/**
 * Key status states returned when verifying existing device keys.
 */
sealed interface DeviceKeyStatus {
    data class Valid(val alias: String) : DeviceKeyStatus
    data class KeyNeedsRotation(val reason: String) : DeviceKeyStatus
    data class NeedsReenrollment(val reason: String, val cause: Throwable? = null) : DeviceKeyStatus
}

open class DeviceKeyException(message: String, cause: Throwable? = null) : GeneralSecurityException(message, cause)
class KeyNeedsRotationException(message: String) : DeviceKeyException(message)
class NeedsReenrollmentException(message: String, cause: Throwable? = null) : DeviceKeyException(message, cause)

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

    private val keyTiers = mutableMapOf<String, AuthTier>()

    /** Checks the status of an existing or requested device key. */
    open fun checkDeviceKey(spec: DeviceKeySpec): DeviceKeyStatus {
        val store = keyStore
        if (!store.containsAlias(spec.alias)) {
            return DeviceKeyStatus.NeedsReenrollment("Device key '${spec.alias}' not found in Keystore")
        }

        // Check if key is accessible / not invalidated
        try {
            val key = store.getKey(spec.alias, null)
            if (key == null) {
                return DeviceKeyStatus.NeedsReenrollment("Device key '${spec.alias}' is null")
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            return DeviceKeyStatus.NeedsReenrollment("Device key permanently invalidated: biometrics or lock screen changed", e)
        } catch (e: UnrecoverableKeyException) {
            return DeviceKeyStatus.NeedsReenrollment("Device key unrecoverable: lock screen removed", e)
        } catch (e: Exception) {
            if (e::class.java.name.contains("KeyPermanentlyInvalidatedException") ||
                e.cause?.let { it::class.java.name.contains("KeyPermanentlyInvalidatedException") } == true) {
                return DeviceKeyStatus.NeedsReenrollment("Device key permanently invalidated", e)
            }
        }

        // Check attestation certificate chain (must have >= 2 certs; 1 cert = self-signed, no attestation)
        val chain = store.getCertificateChain(spec.alias)
        if (chain == null || chain.size < 2) {
            return DeviceKeyStatus.KeyNeedsRotation("Key '${spec.alias}' has no attestation challenge; rotation required")
        }

        // Check tier match if known
        val registeredTier = keyTiers[spec.alias]
        if (registeredTier != null && registeredTier != spec.authTier) {
            return DeviceKeyStatus.KeyNeedsRotation("Key auth tier (${registeredTier.wireValue}) does not match current policy (${spec.authTier.wireValue})")
        }

        return DeviceKeyStatus.Valid(spec.alias)
    }

    /** Creates the key if absent; returns the alias in use, or surfaces typed KeyNeedsRotation/NeedsReenrollment. */
    open fun ensureDeviceKey(spec: DeviceKeySpec): String {
        val store = keyStore
        if (store.containsAlias(spec.alias)) {
            when (val status = checkDeviceKey(spec)) {
                is DeviceKeyStatus.Valid -> return spec.alias
                is DeviceKeyStatus.KeyNeedsRotation -> throw KeyNeedsRotationException(status.reason)
                is DeviceKeyStatus.NeedsReenrollment -> throw NeedsReenrollmentException(status.reason)
            }
        }
        generateKeyPair(spec)
        keyTiers[spec.alias] = spec.authTier
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

        if (spec.authTier == AuthTier.HARDWARE_CONFIRM) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
                builder.setInvalidatedByBiometricEnrollment(false)
            } else {
                builder.setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setInvalidatedByBiometricEnrollment(true)
                }
            }
        } else {
            builder.setUserAuthenticationRequired(false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
        keyTiers.remove(alias)
    }

    companion object {
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
    }
}
