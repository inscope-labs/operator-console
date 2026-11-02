package com.inscopelabs.buildengine.core.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey

/**
 * Reports the real hardware-backing state of a Keystore key.
 *
 * Design review §13: "hardware-backed" is not a claim the app may make unconditionally.
 * This class reads KeyInfo.isInsideSecureHardware and KeyInfo.securityLevel where available,
 * and reports the actual result honestly.
 */
class StrongBoxSupport(
    val context: Context? = null,
    private val hardwareBackedOverride: ((String) -> Boolean)? = null,
    private val securityLevelOverride: ((String) -> String)? = null,
    private val strongBoxAvailableOverride: Boolean? = null,
) {

    fun isStrongBoxAvailable(): Boolean {
        if (strongBoxAvailableOverride != null) return strongBoxAvailableOverride
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val pm = context?.packageManager ?: return false
        return pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }

    fun isHardwareBacked(alias: String): Boolean {
        if (hardwareBackedOverride != null) return hardwareBackedOverride.invoke(alias)
        val keyInfo = getKeyInfo(alias) ?: return false
        return keyInfo.isInsideSecureHardware
    }

    fun securityLevel(alias: String): String {
        if (securityLevelOverride != null) return securityLevelOverride.invoke(alias)
        val keyInfo = getKeyInfo(alias) ?: return "UNKNOWN"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (keyInfo.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TRUSTED_ENVIRONMENT"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
                KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> "UNKNOWN_SECURE"
                else -> "UNKNOWN"
            }
        } else {
            if (keyInfo.isInsideSecureHardware) "TRUSTED_ENVIRONMENT" else "SOFTWARE"
        }
    }

    private fun getKeyInfo(alias: String): KeyInfo? = try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey ?: return null
        val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
        factory.getKeySpec(privateKey, KeyInfo::class.java)
    } catch (_: Exception) {
        null
    }
}
