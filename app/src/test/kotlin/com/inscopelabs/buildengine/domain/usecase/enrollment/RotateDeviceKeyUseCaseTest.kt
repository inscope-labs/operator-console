package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.Base64Url
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.network.dto.EnrollChallengeDto
import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.core.network.dto.RotateKeyRequestDto
import com.inscopelabs.buildengine.core.security.AuthTier
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
import java.security.GeneralSecurityException

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
        enrollmentStore.recordPending("k1", "key-v1", "pub-v1", 1700000000L, AuthTier.HARDWARE_CONFIRM.wireValue)
        enrollmentStore.activate("k1", 1700000000L)
        deviceKeyManager.ensureDeviceKey(
            DeviceKeySpec(
                alias = "key-v1",
                attestationChallenge = byteArrayOf(1, 2, 3),
                authTier = AuthTier.HARDWARE_CONFIRM,
            )
        )

        rotateDeviceKeyUseCase = RotateDeviceKeyUseCase(
            deviceKeyManager = deviceKeyManager,
            deviceAttestation = deviceAttestation,
            gatewayApi = gatewayApi,
            enrollmentStore = enrollmentStore,
            authTierProvider = { AuthTier.HARDWARE_CONFIRM },
        )
    }

    @Test
    fun `successful rotation fetches challenge, sends tier, activates new key, and deletes retired key`() = runTest {
        var lastRotateRequest: RotateKeyRequestDto? = null
        val expectedChallenge = "dGVzdC1jaGFsbGVuZ2UtMzItYnl0ZXMtc3RyaW5nLTAwMQ"
        gatewayApi.enrollChallengeResult = {
            EnrollChallengeDto(challenge = expectedChallenge, expiresAt = 1700000300L)
        }

        gatewayApi.rotateKeyResult = { req ->
            lastRotateRequest = req
            EnrollResponseDto(kid = "k2", status = "active", enrolledAt = 1700000100L, authTier = "hardware_confirm")
        }

        val newKid = rotateDeviceKeyUseCase("key-v1", "key-v2")
        assertEquals("k2", newKid)

        assertEquals(expectedChallenge, lastRotateRequest?.challenge)
        assertEquals("hardware_confirm", lastRotateRequest?.authTier)

        // Verifies new key active
        assertEquals("k2", enrollmentStore.activeKid())
        val record = enrollmentStore.activeRecord()
        assertNotNull(record)
        assertEquals("key-v2", record?.keyAlias)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, record?.status)
        assertEquals("hardware_confirm", record?.authTier)

        // Verifies old key is retired in store
        assertEquals(DeviceEnrollmentStore.Status.RETIRED, enrollmentStore.record("k1")?.status)

        // Verifies old key was deleted from KeyStore and new key remains
        assertFalse(deviceKeyManager.hasDeviceKey("key-v1"))
        assertTrue(deviceKeyManager.hasDeviceKey("key-v2"))
    }

    @Test
    fun `rotation refused when chain size is 1`() = runTest {
        deviceAttestation.chainProvider = { listOf("singleCertOnly") }
        try {
            rotateDeviceKeyUseCase("key-v1", "key-v2")
            fail("Expected refusal when chain size is 1")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("at least 2") == true)
        }
        // Old key preserved, new key deleted
        assertTrue(deviceKeyManager.hasDeviceKey("key-v1"))
    }

    @Test
    fun `rotation retries challenge on ENROLL_CHALLENGE_INVALID`() = runTest {
        var challengeCalls = 0
        gatewayApi.enrollChallengeResult = {
            challengeCalls++
            val c = if (challengeCalls == 1) "dGVzdC1jaGFsbGVuZ2UtMzItYnl0ZXMtc3RyaW5nLTAwMQ" else "dGVzdC1jaGFsbGVuZ2UtMzItYnl0ZXMtc3RyaW5nLTAwMg"
            EnrollChallengeDto(challenge = c, expiresAt = 1700000300L)
        }

        var rotateAttempts = 0
        gatewayApi.rotateKeyResult = { req ->
            rotateAttempts++
            if (rotateAttempts == 1) {
                throw Exception("400: ENROLL_CHALLENGE_INVALID: expired")
            } else {
                EnrollResponseDto(kid = "k2", status = "active", enrolledAt = 1700000100L)
            }
        }

        val newKid = rotateDeviceKeyUseCase("key-v1", "key-v2")
        assertEquals("k2", newKid)
        assertEquals(2, challengeCalls)
        assertEquals(2, rotateAttempts)
    }

    @Test
    fun `rotation throws AuthTierNotAllowedException on AUTH_TIER_NOT_ALLOWED and preserves old key`() = runTest {
        gatewayApi.rotateKeyResult = { _ ->
            throw Exception("403: AUTH_TIER_NOT_ALLOWED: tier not permitted")
        }

        try {
            rotateDeviceKeyUseCase("key-v1", "key-v2")
            fail("Expected AuthTierNotAllowedException")
        } catch (e: AuthTierNotAllowedException) {
            assertTrue(e.message?.contains("not permitted") == true)
        }
        // Old key must be preserved!
        assertTrue(deviceKeyManager.hasDeviceKey("key-v1"))
        assertFalse(deviceKeyManager.hasDeviceKey("key-v2"))
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

        // Old key preserved, new key deleted
        assertTrue(deviceKeyManager.hasDeviceKey("key-v1"))
        assertFalse(deviceKeyManager.hasDeviceKey("key-v2"))
        assertEquals("k1", enrollmentStore.activeKid())
    }
}
