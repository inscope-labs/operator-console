package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDevicePostureUseCaseTest {

    @Test
    fun `reports hardware backed and strongbox posture accurately`() = runTest {
        val deviceKeyManager = FakeDeviceKeyManager()
        val deviceAttestation = FakeDeviceAttestation(deviceKeyManager)
        val strongBoxSupport = StrongBoxSupport(
            hardwareBackedOverride = { true },
            securityLevelOverride = { "STRONGBOX" },
            strongBoxAvailableOverride = true,
        )

        val useCase = ReportDevicePostureUseCase(strongBoxSupport, deviceAttestation)
        val posture = useCase("test-alias")

        assertTrue(posture.hardwareBacked)
        assertTrue(posture.strongBoxAvailable)
        assertEquals("STRONGBOX", posture.securityLevel)
        assertTrue(posture.attested)
        assertFalse(posture.pinned)
        assertTrue(posture.summary.contains("Hardware-backed key"))
        assertTrue(posture.summary.contains("StrongBox available"))
        assertTrue(posture.summary.contains("key attestation present"))
    }

    @Test
    fun `reports software backed posture without false hardware claims`() = runTest {
        val deviceKeyManager = FakeDeviceKeyManager()
        val deviceAttestation = FakeDeviceAttestation(deviceKeyManager).apply {
            chainProvider = { emptyList() }
        }
        val strongBoxSupport = StrongBoxSupport(
            hardwareBackedOverride = { false },
            securityLevelOverride = { "SOFTWARE" },
            strongBoxAvailableOverride = false,
        )

        val useCase = ReportDevicePostureUseCase(strongBoxSupport, deviceAttestation)
        val posture = useCase("test-alias")

        assertFalse(posture.hardwareBacked)
        assertFalse(posture.strongBoxAvailable)
        assertEquals("SOFTWARE", posture.securityLevel)
        assertFalse(posture.attested)
        assertTrue(posture.summary.contains("Software-backed or unverified key"))
        assertTrue(posture.summary.contains("StrongBox unavailable"))
        assertTrue(posture.summary.contains("key attestation unavailable"))
    }
}
