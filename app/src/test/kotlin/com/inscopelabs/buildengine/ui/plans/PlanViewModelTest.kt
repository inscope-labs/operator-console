package com.inscopelabs.buildengine.ui.plans

import app.cash.turbine.test
import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetNextTasksUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.read.RenderCheckUseCase
import com.inscopelabs.buildengine.domain.usecase.read.ValidatePlanUseCase
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusUseCase
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "k2", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )
    private val headSha = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"

    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var operationRepository: RecordingStage34OperationRepository
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var getPlansUseCase: GetPlansUseCase
    private lateinit var getStatusUseCase: GetStatusUseCase
    private lateinit var validatePlanUseCase: ValidatePlanUseCase
    private lateinit var renderCheckUseCase: RenderCheckUseCase
    private lateinit var getNextTasksUseCase: GetNextTasksUseCase
    private lateinit var setTaskStatusUseCase: SetTaskStatusUseCase

    @Before
    fun setUp() {
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository().apply {
            liveHeadResult = { AppResult.Success(headSha) }
            branchResult = { AppResult.Success(TaskBranch("task-10008-parser", headSha)) }
        }
        operationRepository = RecordingStage34OperationRepository(
            defaultOutcome = OperationOutcome.Succeeded(
                auditRef = "audit-in-progress-1",
                headSha = headSha,
                outcome = "in_progress",
            ),
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
        getPlansUseCase = GetPlansUseCase(planRepository)
        getStatusUseCase = GetStatusUseCase(planRepository)
        validatePlanUseCase = ValidatePlanUseCase(planRepository)
        renderCheckUseCase = RenderCheckUseCase(planRepository)
        getNextTasksUseCase = GetNextTasksUseCase(taskRepository)
        setTaskStatusUseCase = SetTaskStatusUseCase(
            operationRepository = operationRepository,
            taskRepository = taskRepository,
            confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
                capabilityMinter = minter,
                biometricGate = BiometricGate { _, _ -> true },
                verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
                clock = clock,
                enrollmentStore = enrollmentStore,
                expectedEnvironmentProvider = { "test" },
                reportedEnvironmentProvider = { "test" },
                hardwareConfirmAvailable = { false },
            ),
            nonceSource = nonceSource,
            planRepository = planRepository,
        )
    }

    private fun buildViewModel(): PlanViewModel = PlanViewModel(
        getPlansUseCase = getPlansUseCase,
        planRepository = planRepository,
        getStatusUseCase = getStatusUseCase,
        validatePlanUseCase = validatePlanUseCase,
        renderCheckUseCase = renderCheckUseCase,
        getNextTasksUseCase = getNextTasksUseCase,
        setTaskStatusUseCase = setTaskStatusUseCase,
        enrollmentStore = enrollmentStore,
    )

    @Test
    fun `init leaves planListState Idle and performs zero repository calls`() = runTest {
        val viewModel = buildViewModel()

        viewModel.planListState.test {
            assertEquals(UiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.planDetailState.test {
            assertEquals(UiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, planRepository.getPlansCallCount)
        assertEquals(0, planRepository.getPlanCallCount)
        assertEquals(0, planRepository.getStatusCallCount)
        assertEquals(0, taskRepository.getNextTasksCallCount)
    }

    @Test
    fun `planListState transitions to Success when plans load`() = runTest {
        val testPlans = listOf(
            Plan(id = "p1", title = "Plan 1", taskCount = 3, completedTaskCount = 1, status = "active"),
        )
        planRepository.plansResult = AppResult.Success(testPlans)

        val viewModel = buildViewModel()

        viewModel.planListState.test {
            assertEquals(UiState.Idle, awaitItem())
            viewModel.loadPlans()
            val state = awaitItem()
            val success = if (state is UiState.Loading) awaitItem() else state
            assertTrue(success is UiState.Success)
            assertEquals(1, (success as UiState.Success).data.size)
            assertEquals("p1", success.data[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `planListState transitions to Error when plans fail`() = runTest {
        planRepository.plansResult = AppResult.Failure(code = "network_error", message = "Network error")

        val viewModel = buildViewModel()

        viewModel.planListState.test {
            assertEquals(UiState.Idle, awaitItem())
            viewModel.loadPlans()
            val state = awaitItem()
            val error = if (state is UiState.Loading) awaitItem() else state
            assertTrue(error is UiState.Error)
            assertEquals("Network error", (error as UiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPlanDetail loads plan, status, and plan-scoped pending tasks`() = runTest {
        val testPlan = Plan(id = "atlas", title = "Project Atlas", taskCount = 5, completedTaskCount = 2, status = "in_progress")
        val testStatus = PlanStatus(id = "atlas", status = "in_progress", halted = false, openBranches = listOf("b1"), blockedTasks = emptyList(), currentMilestone = "M1")
        val matchingPending = Task("10008", "atlas", "Refactor parser", TaskStatus.PENDING, "task-10008-parser", headSha, "order-7731")
        val matchingInProgress = Task("10009", "atlas", "Already started", TaskStatus.IN_PROGRESS, "task-10009-x", headSha, "order-7732")
        val otherPlanPending = Task("20001", "other", "Other plan", TaskStatus.PENDING, "task-20001-y", headSha, "order-9999")

        planRepository.singlePlanResult = { AppResult.Success(testPlan) }
        planRepository.statusResult = { AppResult.Success(testStatus) }
        taskRepository.nextTasksResult = AppResult.Success(listOf(matchingPending, matchingInProgress, otherPlanPending))

        val viewModel = buildViewModel()

        viewModel.planDetailState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadPlanDetail("atlas")

            val state = awaitItem()
            val success = if (state is UiState.Loading) awaitItem() else state
            assertTrue(success is UiState.Success)
            val data = (success as UiState.Success).data
            assertEquals("atlas", data.plan.id)
            assertEquals("Project Atlas", data.plan.title)
            assertNotNull(data.status)
            assertEquals("M1", data.status?.currentMilestone)
            assertEquals(listOf(matchingPending), data.pendingTasks)
            assertEquals(AuthTier.HARDWARE_CONFIRM, data.authTier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startTask executes SET_IN_PROGRESS and removes task from pendingTasks on success`() = runTest {
        val pendingTask = Task("10008", "atlas", "Refactor parser", TaskStatus.PENDING, "task-10008-parser", headSha, "order-7731")
        val updatedStatus = PlanStatus("atlas", "in_progress", false, listOf("task-10008-parser"), emptyList(), "M1")

        planRepository.singlePlanResult = { AppResult.Success(Plan("atlas", "Project Atlas", 5)) }
        planRepository.statusResult = { AppResult.Success(updatedStatus) }
        taskRepository.nextTasksResult = AppResult.Success(listOf(pendingTask))
        taskRepository.singleTaskResult = {
            AppResult.Success(pendingTask.copy(status = TaskStatus.IN_PROGRESS))
        }

        val viewModel = buildViewModel()
        viewModel.loadPlanDetail("atlas")
        assertTrue(viewModel.planDetailState.value is UiState.Success)

        taskRepository.nextTasksResult = AppResult.Success(listOf(pendingTask.copy(status = TaskStatus.IN_PROGRESS)))
        viewModel.startTask(pendingTask)

        val detail = (viewModel.planDetailState.value as UiState.Success).data
        assertTrue(detail.pendingTasks.isEmpty())
        assertEquals(true, detail.transitionFeedback?.accepted)
        assertEquals("audit-in-progress-1", detail.transitionFeedback?.auditRef)

        val (req, targetStatus, cap) = operationRepository.statusTransitions.single()
        assertEquals(OperationType.SET_IN_PROGRESS, req.op)
        assertEquals("in_progress", targetStatus)
        assertEquals(OperationType.SET_IN_PROGRESS, cap.capability.op)
        assertEquals("order-7731", cap.capability.order)
    }

    @Test
    fun `startTask surfaces refusal and preserves pending task when gateway refuses`() = runTest {
        val pendingTask = Task("10008", "atlas", "Refactor parser", TaskStatus.PENDING, "task-10008-parser", headSha, "order-7731")
        operationRepository.defaultOutcome = OperationOutcome.Refused(code = "HALT_ACTIVE")

        planRepository.singlePlanResult = { AppResult.Success(Plan("atlas", "Project Atlas", 5)) }
        planRepository.statusResult = { AppResult.Success(PlanStatus("atlas", "active", true, emptyList(), emptyList(), "M1")) }
        taskRepository.nextTasksResult = AppResult.Success(listOf(pendingTask))

        val viewModel = buildViewModel()
        viewModel.loadPlanDetail("atlas")
        viewModel.startTask(pendingTask)

        val detail = (viewModel.planDetailState.value as UiState.Success).data
        assertEquals(listOf(pendingTask), detail.pendingTasks)
        assertFalse(detail.transitionFeedback?.accepted ?: true)
        assertEquals("HALT_ACTIVE", detail.transitionFeedback?.errorCode)
    }

    @Test
    fun `loadPlanDetail emits Error when plan is not found`() = runTest {
        planRepository.singlePlanResult = { AppResult.Failure(code = "not_found", message = "Plan not found: missing") }

        val viewModel = buildViewModel()

        viewModel.planDetailState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadPlanDetail("missing")

            val state = awaitItem()
            val error = if (state is UiState.Loading) awaitItem() else state
            assertTrue(error is UiState.Error)
            assertEquals("Plan not found: missing", (error as UiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `validatePlan and checkRender update detail state`() = runTest {
        val testPlan = Plan(id = "atlas", title = "Project Atlas", taskCount = 5)
        planRepository.singlePlanResult = { AppResult.Success(testPlan) }
        planRepository.statusResult = { AppResult.Success(PlanStatus("atlas", "in_progress", false, emptyList(), emptyList(), "M1")) }
        planRepository.validateResult = { id -> AppResult.Success(PlanValidation(valid = true, planId = id, errors = emptyList(), warnings = emptyList())) }
        planRepository.renderCheckResult = { id -> AppResult.Success(RenderCheck(ok = true, planId = id, renderIssues = listOf("Issue 1"))) }

        val viewModel = buildViewModel()

        viewModel.planDetailState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadPlanDetail("atlas")
            val loaded = awaitItem()
            val state = if (loaded is UiState.Loading) awaitItem() else loaded
            assertTrue(state is UiState.Success)

            // Trigger validate
            viewModel.validatePlan("atlas")
            val valState = awaitItem()
            assertTrue((valState as UiState.Success).data.validation?.valid == true)

            // Trigger render check
            viewModel.checkRender("atlas")
            val renderState = awaitItem()
            assertTrue((renderState as UiState.Success).data.renderCheck?.ok == true)
            assertEquals(1, (renderState as UiState.Success).data.renderCheck?.renderIssues?.size)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
