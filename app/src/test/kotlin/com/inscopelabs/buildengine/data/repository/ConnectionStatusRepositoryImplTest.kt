package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.domain.model.ConnectionStatus
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStatusRepositoryImplTest {

    @Test
    fun `not configured when base URL is placeholder`() {
        assertEquals(
            ConnectionStatus.NotConfigured,
            deriveConnectionStatus(GatewayEndpoints.BASE_URL, null),
        )
    }

    @Test
    fun `not configured when base URL is empty`() {
        assertEquals(
            ConnectionStatus.NotConfigured,
            deriveConnectionStatus("", null),
        )
    }

    @Test
    fun `unreachable when configured but no health snapshot`() {
        assertEquals(
            ConnectionStatus.Unreachable,
            deriveConnectionStatus("https://gateway.example.com", null),
        )
    }

    @Test
    fun `connected test when health snapshot is test`() {
        val snapshot = GatewayHealthSnapshot(
            status = "ok",
            environment = "test",
            version = "1.2.3",
            timestampMillis = 1000L,
            probedAtEpochSeconds = 10L,
        )
        val status = deriveConnectionStatus("https://gateway.example.com", snapshot)
        assertTrue(status is ConnectionStatus.Connected)
        assertEquals("test", (status as ConnectionStatus.Connected).environment)
        assertEquals("1.2.3", status.version)
    }

    @Test
    fun `connected staging when health snapshot is staging`() {
        val snapshot = GatewayHealthSnapshot(
            status = "ok",
            environment = "staging",
            version = "2.0.0",
            timestampMillis = 1000L,
            probedAtEpochSeconds = 10L,
        )
        val status = deriveConnectionStatus("https://gateway.example.com", snapshot)
        assertTrue(status is ConnectionStatus.Connected)
        assertEquals("staging", (status as ConnectionStatus.Connected).environment)
        assertEquals("2.0.0", status.version)
    }

    @Test
    fun `connected production when health snapshot is production`() {
        val snapshot = GatewayHealthSnapshot(
            status = "ok",
            environment = "production",
            version = "9.0.0",
            timestampMillis = 1000L,
            probedAtEpochSeconds = 10L,
        )
        val status = deriveConnectionStatus("https://gateway.example.com", snapshot)
        assertTrue(status is ConnectionStatus.Connected)
        assertEquals("production", (status as ConnectionStatus.Connected).environment)
    }

    @Test
    fun `environment mismatch when expected environment differs from reported snapshot environment`() {
        val snapshot = GatewayHealthSnapshot(
            status = "ok",
            environment = "test",
            version = "1.0.0",
            timestampMillis = 1000L,
            probedAtEpochSeconds = 10L,
        )
        val status = deriveConnectionStatus(
            baseUrl = "https://gateway.example.com",
            snapshot = snapshot,
            expectedEnvironment = "production",
        )
        assertEquals(
            ConnectionStatus.EnvironmentMismatch(expected = "production", reported = "test"),
            status,
        )
    }
}
