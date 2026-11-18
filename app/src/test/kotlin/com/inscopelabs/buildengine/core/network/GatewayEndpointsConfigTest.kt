package com.inscopelabs.buildengine.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayEndpointsConfigTest {

    @Test
    fun defaultBaseUrlIsNotConfigured() {
        assertFalse(GatewayEndpoints.isConfigured())
        assertFalse(GatewayEndpoints.isConfigured(null))
        assertFalse(GatewayEndpoints.isConfigured(TEST_PLACEHOLDER_URL))
    }

    @Test
    fun blankAndInvalidMarkersAreNotConfigured() {
        assertFalse(GatewayEndpoints.isConfigured(""))
        assertFalse(GatewayEndpoints.isConfigured("   "))
        assertFalse(GatewayEndpoints.isConfigured("$TEST_PLACEHOLDER_URL/"))
        assertFalse(GatewayEndpoints.isConfigured("http://foo.invalid"))
    }

    private companion object {
        const val TEST_PLACEHOLDER_URL = "https://test-placeholder.invalid"
    }

    @Test
    fun realHostsAreConfigured() {
        assertTrue(GatewayEndpoints.isConfigured("https://cbridge.example.com"))
        assertTrue(GatewayEndpoints.isConfigured("http://10.0.2.2:8787", isDebug = true))
        assertTrue(GatewayEndpoints.isConfigured("https://workers.dev.example"))
    }

    @Test
    fun validateUrlRejectsBlankAndEmpty() {
        assertFalse(GatewayEndpoints.validateUrl("").isValid)
        assertFalse(GatewayEndpoints.validateUrl("   ").isValid)
    }

    @Test
    fun validateUrlRejectsCredentialsInUrl() {
        val res = GatewayEndpoints.validateUrl("https://user:pass@gateway.example.com")
        assertFalse(res.isValid)
        assertTrue((res as GatewayEndpoints.UrlValidationResult.Invalid).reason.contains("credentials"))
    }

    @Test
    fun validateUrlRejectsCleartextInRelease() {
        val resRelease = GatewayEndpoints.validateUrl("http://10.0.2.2:8787", isDebug = false)
        assertFalse(resRelease.isValid)
        assertTrue((resRelease as GatewayEndpoints.UrlValidationResult.Invalid).reason.contains("release", ignoreCase = true))

        val resProdRelease = GatewayEndpoints.validateUrl("http://gateway.example.com", isDebug = false)
        assertFalse(resProdRelease.isValid)
    }

    @Test
    fun validateUrlAllowsCleartextInDebugOnlyForLocalHosts() {
        assertTrue(GatewayEndpoints.validateUrl("http://10.0.2.2:8787", isDebug = true).isValid)
        assertTrue(GatewayEndpoints.validateUrl("http://localhost:8787", isDebug = true).isValid)
        assertTrue(GatewayEndpoints.validateUrl("http://127.0.0.1:8787", isDebug = true).isValid)

        // Remote cleartext refused even in debug
        val resRemote = GatewayEndpoints.validateUrl("http://remote.gateway.com", isDebug = true)
        assertFalse(resRemote.isValid)
        assertTrue((resRemote as GatewayEndpoints.UrlValidationResult.Invalid).reason.contains("local test hosts"))
    }

    @Test
    fun validateUrlRejectsQueryAndFragment() {
        val resQuery = GatewayEndpoints.validateUrl("https://gateway.example.com?query=1")
        assertFalse(resQuery.isValid)

        val resFragment = GatewayEndpoints.validateUrl("https://gateway.example.com#section")
        assertFalse(resFragment.isValid)
    }

    @Test
    fun validateUrlAcceptsValidHttps() {
        assertTrue(GatewayEndpoints.validateUrl("https://gateway.example.com").isValid)
        assertTrue(GatewayEndpoints.validateUrl("https://gateway.example.com:8443").isValid)
        assertTrue(GatewayEndpoints.validateUrl("https://operator.staging.workers.dev").isValid)
    }
}
