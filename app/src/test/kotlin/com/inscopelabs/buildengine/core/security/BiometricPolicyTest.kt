package com.inscopelabs.buildengine.core.security

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPolicyTest {

    @Test
    fun `default policy allows strong biometrics and device credential`() {
        val policy = BiometricPolicy()
        assertTrue(policy.allowsDeviceCredential())
        assertFalse(policy.requiresStrongBiometricOnly())
        assertTrue(policy.invalidateOnBiometricEnrollment)
        val expected = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        assertEquals(expected, policy.allowedAuthenticators)
    }

    @Test
    fun `strong biometrics only policy disables device credential`() {
        val policy = BiometricPolicy(
            allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
            requireDeviceCredential = false,
        )
        assertFalse(policy.allowsDeviceCredential())
        assertTrue(policy.requiresStrongBiometricOnly())
    }
}
