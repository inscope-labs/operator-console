package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import java.security.GeneralSecurityException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class EnrollDeviceUseCaseTest {

    private lateinit var deviceKeyManager: FakeDeviceKeyManager
    private lateinit var deviceAttestation: FakeDeviceAttestation
    private lateinit var gatewayApi: FakeGatewayApi
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var enrollDeviceUseCase: EnrollDeviceUseCase

    @Before
    fun setUp() {
        deviceKeyManager = FakeDeviceKeyManager()
        deviceAttestation = FakeDeviceAttestation(deviceKeyManager)
        gatewayApi = FakeGatewayApi()
        enrollmentStore = DeviceEnrollmentStore(preferences = FakeSharedPreferences())
        enrollDeviceUseCase = EnrollDeviceUseCase(
            deviceKeyManager = deviceKeyManager,
            deviceAttestation = deviceAttestation,
            gatewayApi = gatewayApi,
            enrollmentStore = enrollmentStore,
        )
    }

    @Test
    fun `successful enrolment generates key, activates store, and returns kid`() = runTest {
        val kid = enrollDeviceUseCase("operator-v1")
        assertEquals("k1", kid)

        // Verifies key was generated in key manager
        val pubKey = deviceKeyManager.publicKeyBase64("operator-v1")
        assertNotNull(pubKey)

        // Verifies store was activated
        val active = enrollmentStore.activeRecord()
        assertNotNull(active)
        assertEquals("k1", active?.kid)
        assertEquals("operator-v1", active?.keyAlias)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, active?.status)
    }

    @Test
    fun `enrolment refused when alias is invalid`() = runTest {
        try {
            enrollDeviceUseCase("alias with spaces")
            fail("Expected IllegalArgumentException for invalid alias")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `enrolment refused when key attestation chain is empty`() = runTest {
        deviceAttestation.chainProvider = { emptyList() }
        try {
            enrollDeviceUseCase("operator-v1")
            fail("Expected GeneralSecurityException when attestation is empty")
        } catch (_: GeneralSecurityException) {}
    }

    @Test
    fun `enrolment throws when gateway does not return active status`() = runTest {
        gatewayApi.enrollResult = {
            EnrollResponseDto(kid = "k1", status = "rejected")
        }
        try {
            enrollDeviceUseCase("operator-v1")
            fail("Expected IllegalArgumentException for non-active gateway status")
        } catch (e: IllegalArgumentException) {
            assertEquals("Gateway did not activate the device key (status=rejected)", e.message)
        }
    }

    @Test
    fun `enrolment throws when gateway returns invalid kid`() = runTest {
        gatewayApi.enrollResult = {
            EnrollResponseDto(kid = "invalid kid with spaces", status = "active")
        }
        try {
            enrollDeviceUseCase("operator-v1")
            fail("Expected IllegalArgumentException for invalid kid")
        } catch (e: IllegalArgumentException) {
            assertEquals("Gateway returned an invalid key id", e.message)
        }
    }
}
