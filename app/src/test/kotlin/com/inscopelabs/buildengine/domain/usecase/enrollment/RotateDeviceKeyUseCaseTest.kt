package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RotateDeviceKeyUseCaseTest {

    private lateinit var deviceKeyManager: FakeDeviceKeyManager
    private lateinit var deviceAttestation: FakeDeviceAttestation
    private lateinit var gatewayApi: FakeGatewayApi
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var rotateDeviceKeyUseCase: RotateDeviceKeyUseCase

    @Before
    fun setUp() {
        deviceKeyManager = FakeDeviceKeyManager()
        deviceAttestation = FakeDeviceAttestation(deviceKeyManager)
        gatewayApi = FakeGatewayApi()
        enrollmentStore = DeviceEnrollmentStore(preferences = FakeSharedPreferences())

        // Set up active key k1
        enrollmentStore.recordPending("k1", "key-v1", "pub-v1", 1700000000L)
        enrollmentStore.activate("k1", 1700000000L)
        deviceKeyManager.ensureDeviceKey(com.inscopelabs.buildengine.core.crypto.DeviceKeySpec("key-v1"))

        rotateDeviceKeyUseCase = RotateDeviceKeyUseCase(
            deviceKeyManager = deviceKeyManager,
            deviceAttestation = deviceAttestation,
            gatewayApi = gatewayApi,
            enrollmentStore = enrollmentStore,
        )
    }

    @Test
    fun `successful rotation activates new key and deletes retired key`() = runTest {
        gatewayApi.rotateKeyResult = {
            EnrollResponseDto(kid = "k2", status = "active", enrolledAt = 1700000100L)
        }

        val newKid = rotateDeviceKeyUseCase("key-v1", "key-v2")
        assertEquals("k2", newKid)

        // Verifies new key active
        assertEquals("k2", enrollmentStore.activeKid())
        val record = enrollmentStore.activeRecord()
        assertNotNull(record)
        assertEquals("key-v2", record?.keyAlias)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, record?.status)

        // Verifies old key is retired in store
        assertEquals(DeviceEnrollmentStore.Status.RETIRED, enrollmentStore.record("k1")?.status)

        // Verifies old key was deleted from KeyStore and new key remains
        assertFalse(deviceKeyManager.hasDeviceKey("key-v1"))
        assertTrue(deviceKeyManager.hasDeviceKey("key-v2"))
    }

    @Test
    fun `rotation refuses when old and new alias are identical`() = runTest {
        try {
            rotateDeviceKeyUseCase("key-v1", "key-v1")
            fail("Expected IllegalArgumentException when aliases match")
        } catch (e: IllegalArgumentException) {
            assertEquals("New key alias must differ from the current alias", e.message)
        }
    }

    @Test
    fun `rotation refuses when old alias does not match active record`() = runTest {
        try {
            rotateDeviceKeyUseCase("wrong-old-key", "key-v2")
            fail("Expected IllegalArgumentException when old alias does not match")
        } catch (e: IllegalArgumentException) {
            assertEquals("The supplied old alias is not the active enrolled key", e.message)
        }
    }

    @Test
    fun `gateway failure preserves old key and cleans up new uncommitted key`() = runTest {
        gatewayApi.rotateKeyResult = {
            throw RuntimeException("Gateway connection lost")
        }

        try {
            rotateDeviceKeyUseCase("key-v1", "key-v2")
            fail("Expected exception on gateway failure")
        } catch (_: RuntimeException) {}

        // Active key is still k1
        assertEquals("k1", enrollmentStore.activeKid())
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, enrollmentStore.activeRecord()?.status)

        // Old key remains in KeyStore; new key was cleaned up
        assertTrue(deviceKeyManager.hasDeviceKey("key-v1"))
        assertFalse(deviceKeyManager.hasDeviceKey("key-v2"))
    }
}
