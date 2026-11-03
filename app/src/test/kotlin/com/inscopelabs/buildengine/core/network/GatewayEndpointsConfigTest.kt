package com.inscopelabs.buildengine.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayEndpointsConfigTest {

    @Test
    fun defaultBaseUrlIsNotConfigured() {
        assertFalse(GatewayEndpoints.isConfigured())
        assertFalse(GatewayEndpoints.isConfigured(GatewayEndpoints.BASE_URL))
    }

    @Test
    fun blankAndInvalidMarkersAreNotConfigured() {
        assertFalse(GatewayEndpoints.isConfigured(""))
        assertFalse(GatewayEndpoints.isConfigured("   "))
        assertFalse(GatewayEndpoints.isConfigured("https://gateway.invalid/"))
        assertFalse(GatewayEndpoints.isConfigured("http://foo.invalid"))
    }

    @Test
    fun realHostsAreConfigured() {
        assertTrue(GatewayEndpoints.isConfigured("https://cbridge.example.com"))
        assertTrue(GatewayEndpoints.isConfigured("http://10.0.2.2:8787"))
        assertTrue(GatewayEndpoints.isConfigured("https://workers.dev.example"))
    }
}
