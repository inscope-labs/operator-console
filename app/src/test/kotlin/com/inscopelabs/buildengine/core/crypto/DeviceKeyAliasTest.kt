package com.inscopelabs.buildengine.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for canonical device key alias derivation and validation per T2-204 and ADR 0007.
 */
class DeviceKeyAliasTest {

    @Test
    fun `derives namespaced alias from environment and hostname`() {
        val alias = DeviceKeySpec.deriveAlias("staging", "gateway.staging.internal")
        assertEquals("op_staging_gateway_staging_internal", alias)
    }

    @Test
    fun `strips protocol, port, and trailing path from host`() {
        val alias = DeviceKeySpec.deriveAlias("test", "http://10.0.2.2:8787/api/v1")
        assertEquals("op_test_10_0_2_2", alias)

        val httpsAlias = DeviceKeySpec.deriveAlias("production", "https://gw.example.com:443/")
        assertEquals("op_production_gw_example_com", httpsAlias)
    }

    @Test
    fun `same host in different environments produces distinct Keystore aliases`() {
        val stgAlias = DeviceKeySpec.deriveAlias("staging", "gateway.buildengine.io")
        val prodAlias = DeviceKeySpec.deriveAlias("production", "gateway.buildengine.io")
        val testAlias = DeviceKeySpec.deriveAlias("test", "gateway.buildengine.io")

        assertNotEquals(stgAlias, prodAlias)
        assertNotEquals(stgAlias, testAlias)
        assertNotEquals(prodAlias, testAlias)

        assertEquals("op_staging_gateway_buildengine_io", stgAlias)
        assertEquals("op_production_gateway_buildengine_io", prodAlias)
        assertEquals("op_test_gateway_buildengine_io", testAlias)
    }

    @Test
    fun `alias length is capped at 64 characters for Android Keystore compliance`() {
        val veryLongHost = "a".repeat(100) + ".example.com"
        val alias = DeviceKeySpec.deriveAlias("production", veryLongHost)

        assertTrue("Alias length must be <= 64, was ${alias.length}", alias.length <= 64)
        assertTrue("Alias must match Android Keystore alias regex", alias.matches(Regex("[A-Za-z0-9._-]{1,64}")))
        assertTrue("Alias must start with environment prefix", alias.startsWith("op_production_"))
    }

    @Test
    fun `refuses empty or blank environment with IllegalArgumentException`() {
        val r1 = runCatching { DeviceKeySpec.deriveAlias("", "gateway.example.com") }
        assertTrue(r1.isFailure)
        assertTrue(r1.exceptionOrNull() is IllegalArgumentException)

        val r2 = runCatching { DeviceKeySpec.deriveAlias("   ", "gateway.example.com") }
        assertTrue(r2.isFailure)
        assertTrue(r2.exceptionOrNull() is IllegalArgumentException)
    }
}
