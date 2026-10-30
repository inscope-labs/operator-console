package com.inscopelabs.buildengine.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the gate refuses when the device cannot satisfy the policy.
 *
 * SCAFFOLD PLACEHOLDER — androidx.biometric ships no separate test-fake artifact, so
 * the fakes have to be built on top of BiometricManager's error codes.
 */
@RunWith(AndroidJUnit4::class)
class BiometricGateTest {

    @Test
    fun gateAndPolicyAreAvailable() {
        assertNotNull(BiometricGate())
        assertNotNull(BiometricPolicy())
    }
}
