package com.inscopelabs.buildengine.core.security

import androidx.biometric.BiometricManager

/**
 * The authentication policy required before a capability may be minted.
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
) {
    /** Returns true if device credential (PIN/pattern/password) is allowed by this policy. */
    fun allowsDeviceCredential(): Boolean =
        (allowedAuthenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0

    /** Returns true if only strong biometric authenticators are permitted. */
    fun requiresStrongBiometricOnly(): Boolean =
        allowedAuthenticators == BiometricManager.Authenticators.BIOMETRIC_STRONG
}
