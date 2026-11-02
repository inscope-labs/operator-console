package com.inscopelabs.buildengine.core.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.InvalidKeySpecException

/** Reports measured key security, never inferred device capability. */
class StrongBoxSupport(context: Context) {
    private val appContext = context.applicationContext

    fun isStrongBoxAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    fun isHardwareBacked(alias: String): Boolean {
        val key = privateKey(alias)
        return keyInfo(key).isInsideSecureHardware
    }

    fun securityLevel(alias: String): String = securityLevel(privateKey(alias))

    fun keyInfo(privateKey: PrivateKey): KeyInfo = try {
        KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
            .getKeySpec(privateKey, KeyInfo::class.java)
    } catch (error: InvalidKeySpecException) {
        throw GeneralSecurityException("Unable to inspect Android Keystore key properties", error)
    }

    private fun securityLevel(privateKey: PrivateKey): String {
        val info = keyInfo(privateKey)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TRUSTED_ENVIRONMENT"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                else -> "UNKNOWN"
            }
        }
        return if (info.isInsideSecureHardware) "HARDWARE_BACKED" else "SOFTWARE"
    }

    private fun privateKey(alias: String): PrivateKey {
        require(alias.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid Android Keystore alias" }
        val store = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return store.getKey(alias, null) as? PrivateKey
            ?: throw GeneralSecurityException("No private key exists for alias")
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
