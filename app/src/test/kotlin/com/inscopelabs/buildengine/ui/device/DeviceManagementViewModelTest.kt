package com.inscopelabs.buildengine.ui.device

import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.dto.AdminEnrolmentsResponseDto
import com.inscopelabs.buildengine.core.network.dto.EnrolledDeviceDto
import com.inscopelabs.buildengine.core.network.dto.RevokeKeyResponseDto
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.usecase.enrollment.RevokeDeviceKeyUseCase
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [DeviceManagementViewModel] (PoC Task 24 / PR3-032).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var preferences: FakeSharedPreferences
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var deviceKeyManager: FakeDeviceKeyManager
    private lateinit var gatewayApi: FakeGatewayApi

    private val localSpec = DeviceKeySpec(
        alias = "op-local-v1",
        authTier = AuthTier.HARDWARE_CONFIRM,
        attestationChallenge = byteArrayOf(1, 2, 3),
    )

    @Before
    fun setUp() {
        preferences = FakeSharedPreferences()
        enrollmentStore = DeviceEnrollmentStore(preferences = preferences).apply {
            recordPending(
                kid = "k1",
                keyAlias = "op-local-v1",
                publicKeyBase64 = "BPubLocal",
                now = 1700000000L,
                authTier = AuthTier.HARDWARE_CONFIRM.wireValue,
            )
            activate("k1", 1700000000L)
        }
        deviceKeyManager = FakeDeviceKeyManager().apply {
            ensureDeviceKey(localSpec)
        }
        gatewayApi = FakeGatewayApi().apply {
            listEnrolmentsResult = {
                AdminEnrolmentsResponseDto(
                    enrolments = listOf(
                        EnrolledDeviceDto("k1", "op-local-v1", "hardware_confirm", "active", 1700000000L),
                        EnrolledDeviceDto("k2", "op-tablet-v1", "in_app_confirm", "active", 1700000050L),
                    ),
                    count = 2,
                )
            }
        }
    }

    private fun createViewModel(): DeviceManagementViewModel {
        val revokeUseCase = RevokeDeviceKeyUseCase(
            gatewayApi = gatewayApi,
            enrollmentStore = enrollmentStore,
            deleteLocalKey = { alias -> deviceKeyManager.deleteDeviceKey(alias) },
            nowEpochSeconds = { 1700000500L },
        )
        return DeviceManagementViewModel(
            gatewayApi = gatewayApi,
            enrollmentStore = enrollmentStore,
            deviceKeyManager = deviceKeyManager,
            revokeDeviceKeyUseCase = revokeUseCase,
            errorMapper = ErrorMapper(),
        )
    }

    @Test
    fun `initial state is Idle and makes no network requests`() {
        var listCalls = 0
        gatewayApi.listEnrolmentsResult = {
            listCalls++
            AdminEnrolmentsResponseDto()
        }
        val vm = createViewModel()
        assertEquals(UiState.Idle, vm.uiState.value)
        assertEquals(0, listCalls)
    }

    @Test
    fun `load lists enrolments identifies local active kid and detects valid key`() = runTest {
        val vm = createViewModel()
        vm.load()

        val state = vm.uiState.value as UiState.Success
        assertEquals(2, state.data.devices.size)
        assertEquals("k1", state.data.localActiveKid)
        assertFalse(state.data.needsReenrollment)
        assertNull(state.data.keyStatusReason)
    }

    @Test
    fun `load surfaces NeedsReenrollment when local key is permanently invalidated`() = runTest {
        deviceKeyManager.simulatedInvalidated = true
        deviceKeyManager.simulatedInvalidatedReason = "Biometric enrollment changed; re-enrol"

        val vm = createViewModel()
        vm.load()

        val state = vm.uiState.value as UiState.Success
        assertTrue(state.data.needsReenrollment)
        assertEquals("Biometric enrollment changed; re-enrol", state.data.keyStatusReason)
    }

    @Test
    fun `revoking local active device routes through RevokeDeviceKeyUseCase and sets needsReenrollment`() = runTest {
        gatewayApi.revokeKeyResult = { req ->
            assertEquals("REVOKE k1", req.confirmation)
            RevokeKeyResponseDto(revoked = true, kid = req.kid, revokedAt = 1700000600L)
        }
        // Subsequent refresh after revocation reflects retired local device removed from active list
        var listCount = 0
        gatewayApi.listEnrolmentsResult = {
            listCount++
            if (listCount == 1) {
                AdminEnrolmentsResponseDto(
                    enrolments = listOf(
                        EnrolledDeviceDto("k1", "op-local-v1", "hardware_confirm", "active", 1700000000L),
                        EnrolledDeviceDto("k2", "op-tablet-v1", "in_app_confirm", "active", 1700000050L),
                    ),
                    count = 2,
                )
            } else {
                AdminEnrolmentsResponseDto(
                    enrolments = listOf(
                        EnrolledDeviceDto("k2", "op-tablet-v1", "in_app_confirm", "active", 1700000050L),
                    ),
                    count = 1,
                )
            }
        }

        val vm = createViewModel()
        vm.load()
        vm.revoke(kid = "k1", reason = "Rotating lost hardware")

        val state = vm.uiState.value as UiState.Success
        assertTrue(state.data.needsReenrollment)
        assertEquals("", state.data.localActiveKid)
        assertNull(enrollmentStore.activeRecord())
        assertFalse(deviceKeyManager.hasDeviceKey("op-local-v1"))
        val outcome = checkNotNull(state.data.revokeOutcome)
        assertTrue(outcome.revoked)
        assertEquals("k1", outcome.kid)
        assertEquals(1700000600L, outcome.revokedAtEpochSeconds)
        assertEquals(listOf("k2"), state.data.devices.map { it.kid })
    }

    @Test
    fun `failed local revocation keeps local enrolment intact and reports failure`() = runTest {
        gatewayApi.revokeKeyResult = {
            throw GatewayHttpException(
                statusCode = 403,
                body = """{"code":"AUTH_INVALID","message":"forbidden"}""",
                message = "forbidden",
            )
        }

        val vm = createViewModel()
        vm.load()
        vm.revoke(kid = "k1", reason = "Operator requested")

        val state = vm.uiState.value as UiState.Success
        assertEquals("k1", enrollmentStore.activeKid())
        assertTrue(deviceKeyManager.hasDeviceKey("op-local-v1"))
        assertFalse(state.data.needsReenrollment)
        val outcome = checkNotNull(state.data.revokeOutcome)
        assertFalse(outcome.revoked)
        assertNotNull(outcome.errorMessage)
        assertTrue(outcome.errorMessage!!.contains("403"))
    }

    @Test
    fun `revoking non-local device sends REVOKE confirmation and preserves local enrolment`() = runTest {
        gatewayApi.revokeKeyResult = { req ->
            assertEquals("REVOKE k2", req.confirmation)
            RevokeKeyResponseDto(revoked = true, kid = req.kid, revokedAt = 1700000700L)
        }

        val vm = createViewModel()
        vm.load()
        vm.revoke(kid = "k2", reason = "Tablet decommissioned")

        val state = vm.uiState.value as UiState.Success
        assertEquals("k1", enrollmentStore.activeKid())
        assertEquals("k1", state.data.localActiveKid)
        assertFalse(state.data.needsReenrollment)
        val outcome = checkNotNull(state.data.revokeOutcome)
        assertTrue(outcome.revoked)
        assertEquals("k2", outcome.kid)
    }
}
