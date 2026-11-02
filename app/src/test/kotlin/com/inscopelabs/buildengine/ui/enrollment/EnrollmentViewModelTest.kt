package com.inscopelabs.buildengine.ui.enrollment

import app.cash.turbine.test
import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.usecase.enrollment.EnrollDeviceUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.RotateDeviceKeyUseCase
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnrollmentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var deviceKeyManager: FakeDeviceKeyManager
    private lateinit var deviceAttestation: FakeDeviceAttestation
    private lateinit var gatewayApi: FakeGatewayApi
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var strongBoxSupport: StrongBoxSupport

    private lateinit var enrollDeviceUseCase: EnrollDeviceUseCase
    private lateinit var rotateDeviceKeyUseCase: RotateDeviceKeyUseCase
    private lateinit var reportDevicePostureUseCase: ReportDevicePostureUseCase
    private lateinit var viewModel: EnrollmentViewModel

    @Before
    fun setUp() {
        deviceKeyManager = FakeDeviceKeyManager()
        deviceAttestation = FakeDeviceAttestation(deviceKeyManager)
        gatewayApi = FakeGatewayApi()
        enrollmentStore = DeviceEnrollmentStore(preferences = FakeSharedPreferences())
        strongBoxSupport = StrongBoxSupport(
            hardwareBackedOverride = { true },
            securityLevelOverride = { "STRONGBOX" },
            strongBoxAvailableOverride = true,
        )

        enrollDeviceUseCase = EnrollDeviceUseCase(deviceKeyManager, deviceAttestation, gatewayApi, enrollmentStore)
        rotateDeviceKeyUseCase = RotateDeviceKeyUseCase(deviceKeyManager, deviceAttestation, gatewayApi, enrollmentStore)
        reportDevicePostureUseCase = ReportDevicePostureUseCase(strongBoxSupport, deviceAttestation)

        viewModel = EnrollmentViewModel(
            enrollDevice = enrollDeviceUseCase,
            rotateDeviceKey = rotateDeviceKeyUseCase,
            reportDevicePosture = reportDevicePostureUseCase,
        ).apply {
            ioDispatcher = UnconfinedTestDispatcher()
        }
    }

    @Test
    fun `refreshPosture updates posture in state`() = runTest {
        viewModel.uiState.test {
            assertEquals(EnrollmentUiState(), awaitItem())

            viewModel.refreshPosture("op-alias")

            val updated = awaitItem()
            assertNotNull(updated.posture)
            assertEquals("STRONGBOX", updated.posture?.securityLevel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enroll updates activeKid and posture on success`() = runTest {
        viewModel.uiState.test {
            assertEquals(EnrollmentUiState(), awaitItem())

            viewModel.enroll("op-alias-1")

            // UnconfinedTestDispatcher updates synchronously
            var state = awaitItem()
            while (state.loading || state.activeKid == null) {
                state = awaitItem()
            }

            assertEquals("k1", state.activeKid)
            assertEquals("Device enrolled as k1", state.message)
            assertNull(state.error)
            assertNotNull(state.posture)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enroll records error on failure`() = runTest {
        deviceAttestation.chainProvider = { emptyList() }

        viewModel.uiState.test {
            assertEquals(EnrollmentUiState(), awaitItem())

            viewModel.enroll("op-alias-1")

            var state = awaitItem()
            while (state.loading || state.error == null) {
                state = awaitItem()
            }

            assertNotNull(state.error)
            assertNull(state.activeKid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rotate updates activeKid on success`() = runTest {
        // Pre-enroll k1
        enrollDeviceUseCase("old-alias")

        viewModel.uiState.test {
            assertEquals(EnrollmentUiState(), awaitItem())

            viewModel.rotate("old-alias", "new-alias")

            var state = awaitItem()
            while (state.loading || state.activeKid == null) {
                state = awaitItem()
            }

            assertEquals("k2", state.activeKid)
            assertEquals("Device key rotated; active key id is k2", state.message)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
