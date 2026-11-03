package com.inscopelabs.buildengine.lab

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabEnvironmentGuardTest {

    @Test
    fun `null snapshot is refused`() {
        val result = LabEnvironmentGuard.requireTestEnvironment(null)
        assertTrue(result is AppResult.Failure)
        assertEquals(LabEnvironmentGuard.ENVIRONMENT_MISMATCH_CODE, (result as AppResult.Failure).code)
    }

    @Test
    fun `production environment is refused`() {
        val snapshot = GatewayHealthSnapshot(
            status = "ok",
            environment = "production",
            version = "1.0.0",
            timestampMillis = 1L,
            probedAtEpochSeconds = 1L,
        )
        val result = LabEnvironmentGuard.requireTestEnvironment(snapshot)
        assertTrue(result is AppResult.Failure)
        assertEquals(LabEnvironmentGuard.ENVIRONMENT_MISMATCH_CODE, (result as AppResult.Failure).code)
    }

    @Test
    fun `test environment is accepted`() {
        val snapshot = GatewayHealthSnapshot(
            status = "ok",
            environment = "test",
            version = "1.0.0",
            timestampMillis = 1L,
            probedAtEpochSeconds = 1L,
        )
        val result = LabEnvironmentGuard.requireTestEnvironment(snapshot)
        assertTrue(result is AppResult.Success)
        assertEquals("test", (result as AppResult.Success).value.environment)
    }

    @Test
    fun `empty environment is refused`() {
        val snapshot = GatewayHealthSnapshot(
            status = "ok",
            environment = "",
            version = "1.0.0",
            timestampMillis = 1L,
            probedAtEpochSeconds = 1L,
        )
        val result = LabEnvironmentGuard.requireTestEnvironment(snapshot)
        assertTrue(result is AppResult.Failure)
    }
}
