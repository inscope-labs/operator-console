package com.inscopelabs.buildengine.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricGateTest {

    @Test
    fun `canAuthenticate returns simulated result when set`() {
        val gate = BiometricGate()
        gate.simulatedCanAuthenticateResult = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, gate.canAuthenticate())
    }

    @Test
    fun `authenticate delegates to custom authenticator`() = runTest {
        var called = false
        val gate = BiometricGate(
            authenticator = { policy, _ ->
                called = true
                policy.allowsDeviceCredential()
            }
        )
        val result = gate.authenticate()
        assertTrue(called)
        assertTrue(result)
    }

    @Test
    fun `authenticate delegates to prompt host when available`() = runTest {
        val gate = BiometricGate()
        var hostInvoked = false
        gate.promptHost = object : BiometricPromptHost {
            override suspend fun showPrompt(
                info: PromptInfoSpec,
                cryptoObject: BiometricPrompt.CryptoObject?,
                policy: BiometricPolicy,
            ): BiometricPromptResult {
                hostInvoked = true
                return BiometricPromptResult.Success(cryptoObject)
            }
        }
        val result = gate.authenticate()
        assertTrue(hostInvoked)
        assertTrue(result)
    }

    @Test
    fun `authenticate fails closed when hardware unavailable and no host`() = runTest {
        val gate = BiometricGate()
        gate.simulatedCanAuthenticateResult = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        val result = gate.authenticate()
        assertFalse(result)
    }

    @Test
    fun `authenticate fails closed when no UI host attached`() = runTest {
        val gate = BiometricGate()
        // Default gate with no host and no authenticator fails closed
        val result = gate.authenticate()
        assertFalse(result)
    }
}
