package com.inscopelabs.buildengine.ui.operations

import app.cash.turbine.test
import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.enrollment.ReportDevicePostureUseCase
import com.inscopelabs.buildengine.domain.usecase.write.HaltUseCase
import com.inscopelabs.buildengine.domain.usecase.write.ResumeUseCase
import com.inscopelabs.buildengine.testutil.FakeDeviceAttestation
import com.inscopelabs.buildengine.testutil.FakeDeviceKeyManager
import com.inscopelabs.buildengine.testutil.FakePlanRepository
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.testutil.RecordingStage34OperationRepository
import com.inscopelabs.buildengine.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [HaltResumeViewModel] (PoC Task 20 / T4-401).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HaltResumeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val engineHead = "c".repeat(40)
    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "k2", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )

    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var operationRepository: RecordingStage34OperationRepository
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var postureUseCase: ReportDevicePostureUseCase

    private var currentHalted = false
    private var promptCount = 0
    private var promptAllowed = true

    @Before
    fun setUp() {
        currentHalted = false
        promptCount = 0
        promptAllowed = true

        planRepository = FakePlanRepository().apply {
            plansResult = AppResult.Success(
                listOf(Plan(id = "atlas", title = "Atlas", taskCount = 2, status = "in_progress")),
            )
            statusResult = { planId ->
                AppResult.Success(
                    PlanStatus(
                        id = planId,
                        status = "in_progress",
                        halted = currentHalted,
                        openBranches = emptyList(),
                        blockedTasks = emptyList(),
                        currentMilestone = "M3",
                    ),
                )
            }
        }
        taskRepository = FakeTaskRepository().apply {
            liveHeadResult = { branch ->
                AppResult.Success(if (branch == ENGINE_BRANCH) engineHead else "d".repeat(40))
            }
            nextTasksResult = AppResult.Success(
                listOf(
                    Task(
                        id = "10008",
                        planId = "atlas",
                        title = "Parser task",
                        status = TaskStatus.PENDING,
                        branch = "task-10008-parser",
                        headSha = "d".repeat(40),
                        order = "order-7731",
                    ),
                ),
            )
        }
        operationRepository = RecordingStage34OperationRepository(
            defaultOutcome = OperationOutcome.Succeeded(auditRef = "audit-eng-1", headSha = engineHead),
        )
        enrollmentStore = DeviceEnrollmentStore(preferences = FakeSharedPreferences()).apply {
            recordPending(
                kid = "k2",
                keyAlias = "op-key-v1",
                publicKeyBase64 = "BPubKey",
                now = 1700000000L,
                authTier = AuthTier.HARDWARE_CONFIRM.wireValue,
                environment = "test",
            )
            activate("k2", 1700000001L, "test")
        }
        val keyMgr = FakeDeviceKeyManager()
        postureUseCase = ReportDevicePostureUseCase(
            strongBoxSupport = StrongBoxSupport(context = null),
            deviceAttestation = FakeDeviceAttestation(keyMgr),
            enrollmentStore = enrollmentStore,
        )
    }

    private fun buildViewModel(): HaltResumeViewModel {
        val gate = BiometricGate { _, _ ->
            promptCount++
            promptAllowed
        }
        val confirmAndMint = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = gate,
            verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
            clock = clock,
            enrollmentStore = enrollmentStore,
            expectedEnvironmentProvider = { "test" },
            reportedEnvironmentProvider = { "test" },
            hardwareConfirmAvailable = { false },
        )
        return HaltResumeViewModel(
            haltUseCase = HaltUseCase(operationRepository, confirmAndMint, nonceSource, planRepository),
            resumeUseCase = ResumeUseCase(operationRepository, confirmAndMint, nonceSource, planRepository),
            operationRepository = operationRepository,
            planRepository = planRepository,
            taskRepository = taskRepository,
            enrollmentStore = enrollmentStore,
            reportDevicePostureUseCase = postureUseCase,
            capabilityMinter = minter,
        )
    }

    @Test
    fun `init stays Idle and performs zero repository calls`() = runTest {
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, planRepository.getPlansCallCount)
        assertEquals(0, planRepository.getStatusCallCount)
        assertEquals(0, taskRepository.getNextTasksCallCount)
    }

    @Test
    fun `load populates engine state, tier, posture, TTL, and recorded halt cause`() = runTest {
        currentHalted = true
        operationRepository.haltCause = "Gateway maintenance window"
        val viewModel = buildViewModel()

        viewModel.load()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        val data = (state as UiState.Success).data
        assertTrue(data.engine.isHalted)
        assertEquals("Gateway maintenance window", data.engine.haltReason)
        assertEquals(engineHead, data.engine.engineHeadSha)
        assertEquals(ENGINE_BRANCH, data.engine.branch)
        assertEquals(AuthTier.HARDWARE_CONFIRM, data.authTier)
        assertEquals(300L, data.capabilityTtlSeconds)
        assertTrue(data.posture != null)
    }

    @Test
    fun `load emits Error when engine head is unavailable`() = runTest {
        taskRepository.liveHeadResult = { AppResult.Failure(code = "unreachable", message = "Gateway offline") }
        val viewModel = buildViewModel()

        viewModel.load()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Gateway offline", (state as UiState.Error).message)
    }

    @Test
    fun `halt transitions running engine to halted with engine-scoped capability and outcome banner`() = runTest {
        val viewModel = buildViewModel()
        viewModel.load()

        currentHalted = true
        viewModel.halt("CI nondeterminism detected")

        val data = (viewModel.uiState.value as UiState.Success).data
        assertTrue(data.engine.isHalted)
        assertEquals("CI nondeterminism detected", data.engine.haltReason)
        assertEquals("HALT", data.lastOutcome?.operation)
        assertEquals(true, data.lastOutcome?.accepted)
        assertEquals("audit-eng-1", data.lastOutcome?.auditRef)
        assertEquals(1, promptCount)

        val (req, reason, cap) = operationRepository.halts.single()
        assertEquals(OperationType.HALT, req.op)
        assertEquals("engine", cap.capability.branch)
        assertEquals("CI nondeterminism detected", reason)
    }

    @Test
    fun `refused halt keeps engine running and surfaces error code without claiming halted`() = runTest {
        operationRepository.defaultOutcome = OperationOutcome.Refused(code = "SCOPE_REFUSED")
        val viewModel = buildViewModel()
        viewModel.load()

        viewModel.halt("Emergency stop")

        val data = (viewModel.uiState.value as UiState.Success).data
        assertFalse(data.engine.isHalted)
        assertEquals(false, data.lastOutcome?.accepted)
        assertEquals("SCOPE_REFUSED", data.lastOutcome?.errorCode)
    }

    @Test
    fun `resume refuses when recorded halt cause is missing`() = runTest {
        currentHalted = true
        operationRepository.haltCause = null
        val viewModel = buildViewModel()
        viewModel.load()

        viewModel.resume()

        val data = (viewModel.uiState.value as UiState.Success).data
        assertTrue(data.engine.isHalted)
        assertEquals(false, data.lastOutcome?.accepted)
        assertEquals("MISSING_RECORDED_CAUSE", data.lastOutcome?.errorCode)
        assertEquals(0, promptCount)
        assertTrue(operationRepository.resumes.isEmpty())
    }

    @Test
    fun `resume succeeds when halt cause is recorded and clears engine halted state`() = runTest {
        currentHalted = true
        operationRepository.haltCause = "Incident resolved"
        val viewModel = buildViewModel()
        viewModel.load()

        currentHalted = false
        viewModel.resume()

        val data = (viewModel.uiState.value as UiState.Success).data
        assertFalse(data.engine.isHalted)
        assertEquals("", data.engine.haltReason)
        assertEquals("RESUME", data.lastOutcome?.operation)
        assertEquals(true, data.lastOutcome?.accepted)
        assertEquals("Incident resolved", operationRepository.resumes.single().second)
    }
}
