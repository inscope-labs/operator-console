package com.inscopelabs.buildengine.core.security

import androidx.biometric.BiometricManager

/**
 * The authentication policy required before a capability may be minted.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: the device credential must be included explicitly if it is to be
 * accepted on devices without a biometric sensor.
 */
data class BiometricPolicy(
    val allowedAuthenticators: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
    val requireDeviceCredential: Boolean = true,
    /**
     * Design review §13: never set setInvalidatedByBiometricEnrollment(false) on the
     * operator credential — that would let a newly enrolled biometric mint capabilities.
     */
    val invalidateOnBiometricEnrollment: Boolean = true,
)
