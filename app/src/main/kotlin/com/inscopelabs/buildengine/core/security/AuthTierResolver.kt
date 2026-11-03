package com.inscopelabs.buildengine.core.security

import androidx.biometric.BiometricManager

/**
 * Resolves the authentication tier based on platform capabilities.
 *
 * Pure function of injected facts (sdkInt, isDeviceSecure, biometricStatus)
 * for full JVM testability without Android device dependencies.
 */
object AuthTierResolver {

    /**
     * Resolves the confirmation tier for the device environment.
     *
     * - [AuthTier.HARDWARE_CONFIRM]: Keystore-enforced per-use authentication.
     *   On API >= 30, authenticators BIOMETRIC_STRONG or DEVICE_CREDENTIAL are used, so
     *   [isDeviceSecure] (PIN/pattern/password) is sufficient even with no biometric hardware.
     *   On API 28-29, BIOMETRIC_STRONG only, and only if [biometricStatus] is BIOMETRIC_SUCCESS.
     * - [AuthTier.IN_APP_CONFIRM]: Everything else, including when no secure lock screen is set.
     */
    fun resolve(
        sdkInt: Int,
        isDeviceSecure: Boolean,
        biometricStatus: Int,
    ): AuthTier {
        return when {
            sdkInt >= 30 -> {
                if (isDeviceSecure) {
                    AuthTier.HARDWARE_CONFIRM
                } else {
                    AuthTier.IN_APP_CONFIRM
                }
            }
            sdkInt in 28..29 -> {
                if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                    AuthTier.HARDWARE_CONFIRM
                } else {
                    AuthTier.IN_APP_CONFIRM
                }
            }
            else -> AuthTier.IN_APP_CONFIRM
        }
    }
}
