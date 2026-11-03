package com.inscopelabs.buildengine.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the gate and policy are constructable and available.
 */
@RunWith(AndroidJUnit4::class)
class BiometricGateTest {

    @Test
    fun gateAndPolicyAreAvailable() {
        assertNotNull(BiometricGate())
        assertNotNull(BiometricPolicy())
    }
}
