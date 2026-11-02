package com.inscopelabs.buildengine.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceKeySpecTest {

    @Test
    fun `default parameters match required security profile`() {
        val spec = DeviceKeySpec(alias = "op-key-v1")
        assertEquals("op-key-v1", spec.alias)
        assertEquals("EC", spec.keyAlgorithm)
        assertEquals("secp256r1", spec.curve)
        assertTrue(spec.userAuthenticationRequired)
        assertTrue(spec.preferStrongBox)
    }

    @Test
    fun `equals and hashCode handle attestationChallenge`() {
        val c1 = byteArrayOf(1, 2, 3)
        val c2 = byteArrayOf(1, 2, 3)
        val c3 = byteArrayOf(4, 5, 6)

        val spec1 = DeviceKeySpec("a1", attestationChallenge = c1)
        val spec2 = DeviceKeySpec("a1", attestationChallenge = c2)
        val spec3 = DeviceKeySpec("a1", attestationChallenge = c3)

        assertEquals(spec1, spec2)
        assertEquals(spec1.hashCode(), spec2.hashCode())
        assertNotEquals(spec1, spec3)
    }
}
