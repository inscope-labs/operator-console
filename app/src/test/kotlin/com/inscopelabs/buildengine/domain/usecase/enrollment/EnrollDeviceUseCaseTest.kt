package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.Base64Url
import com.inscopelabs.buildengine.core.network.dto.EnrollChallengeDto
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException

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
            authTierProvider = { AuthTier.HARDWARE_CONFIRM },
        )
    }

    @Test
    fun `successful enrolment fetches challenge first, creates key with challenge, sends tier, and activates store`() = runTest {
        var challengeFetched = false
        var challengeUsedInKeyCreation: ByteArray? = null
        var lastEnrollRequest: EnrollRequestDto? = null

        val expectedChallengeStr = "dGVzdC1jaGFsbGVuZ2UtMzItYnl0ZXMtc3RyaW5nLTAwMQ"
        gatewayApi.enrollChallengeResult = {
            challengeFetched = true
            EnrollChallengeDto(challenge = expectedChallengeStr, expiresAt = 1700000300L)
        }

        gatewayApi.enrollResult = { req ->
            lastEnrollRequest = req
            EnrollResponseDto(kid = "k1", status = "active", enrolledAt = 1700000000L, authTier = "hardware_confirm")
        }

        val kid = enrollDeviceUseCase("operator-v1")
        assertEquals("k1", kid)

        assertTrue("Challenge must be fetched first", challengeFetched)
        challengeUsedInKeyCreation = deviceKeyManager.lastSpec?.attestationChallenge
        assertNotNull(challengeUsedInKeyCreation)
        assertEquals(expectedChallengeStr, Base64Url.encode(challengeUsedInKeyCreation!!))

        assertEquals(expectedChallengeStr, lastEnrollRequest?.challenge)
        assertEquals("hardware_confirm", lastEnrollRequest?.authTier)

        val active = enrollmentStore.activeRecord()
        assertNotNull(active)
        assertEquals("k1", active?.kid)
        assertEquals("operator-v1", active?.keyAlias)
        assertEquals(DeviceEnrollmentStore.Status.ACTIVE, active?.status)
        assertEquals("hardware_confirm", active?.authTier)
    }

    @Test
    fun `enrolment refused when key attestation chain size is 1`() = runTest {
        deviceAttestation.chainProvider = { listOf("singleSelfSignedCertOnly") }
        try {
            enrollDeviceUseCase("operator-v1")
            fail("Expected GeneralSecurityException when attestation chain size is 1")
        } catch (e: GeneralSecurityException) {
            assertTrue(e.message?.contains("at least 2") == true)
        }
    }

    @Test
    fun `enrolment retries fetching challenge once when ENROLL_CHALLENGE_INVALID occurs`() = runTest {
        var challengeCalls = 0
        gatewayApi.enrollChallengeResult = {
            challengeCalls++
            val c = if (challengeCalls == 1) "dGVzdC1jaGFsbGVuZ2UtMzItYnl0ZXMtc3RyaW5nLTAwMQ" else "dGVzdC1jaGFsbGVuZ2UtMzItYnl0ZXMtc3RyaW5nLTAwMg"
            EnrollChallengeDto(challenge = c, expiresAt = 1700000300L)
        }

        var enrollAttempts = 0
        gatewayApi.enrollResult = { req ->
            enrollAttempts++
            if (enrollAttempts == 1) {
                throw Exception("400: ENROLL_CHALLENGE_INVALID: challenge expired")
            } else {
                EnrollResponseDto(kid = "k1", status = "active", enrolledAt = 1700000000L)
            }
        }

        val kid = enrollDeviceUseCase("operator-v1")
        assertEquals("k1", kid)
        assertEquals(2, challengeCalls)
        assertEquals(2, enrollAttempts)
    }

    @Test
    fun `enrolment throws AuthTierNotAllowedException when gateway returns AUTH_TIER_NOT_ALLOWED`() = runTest {
        gatewayApi.enrollResult = { _ ->
            throw Exception("403: AUTH_TIER_NOT_ALLOWED: tier not permitted")
        }

        try {
            enrollDeviceUseCase("operator-v1")
            fail("Expected AuthTierNotAllowedException")
        } catch (e: AuthTierNotAllowedException) {
            assertTrue(e.message?.contains("not permitted") == true)
        }
    }

    @Test
    fun `enrolment refused when alias is invalid`() = runTest {
        try {
            enrollDeviceUseCase("alias with spaces")
            fail("Expected IllegalArgumentException for invalid alias")
        } catch (_: IllegalArgumentException) {}
    }
}
