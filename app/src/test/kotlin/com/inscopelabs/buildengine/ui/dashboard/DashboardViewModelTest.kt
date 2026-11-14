package com.inscopelabs.buildengine.ui.dashboard

import app.cash.turbine.test
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.DoctorCheck
import com.inscopelabs.buildengine.domain.model.DoctorReport
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.SyncStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.usecase.read.GetDoctorReportUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetNextTasksUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.read.SyncBranchUseCase
import com.inscopelabs.buildengine.testutil.FakePlanRepository
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var getPlansUseCase: GetPlansUseCase
    private lateinit var getNextTasksUseCase: GetNextTasksUseCase
    private lateinit var getStatusUseCase: GetStatusUseCase
    private lateinit var syncBranchUseCase: SyncBranchUseCase
    private lateinit var getDoctorReportUseCase: GetDoctorReportUseCase

    @Before
    fun setUp() {
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository()
        getPlansUseCase = GetPlansUseCase(planRepository)
        getNextTasksUseCase = GetNextTasksUseCase(taskRepository)
        getStatusUseCase = GetStatusUseCase(planRepository)
        syncBranchUseCase = SyncBranchUseCase(planRepository)
        getDoctorReportUseCase = GetDoctorReportUseCase(planRepository)
    }

    private fun createViewModel(): DashboardViewModel = DashboardViewModel(
        getPlansUseCase = getPlansUseCase,
        getNextTasksUseCase = getNextTasksUseCase,
        getStatusUseCase = getStatusUseCase,
        syncBranchUseCase = syncBranchUseCase,
        getDoctorReportUseCase = getDoctorReportUseCase,
    )

    @Test
    fun `init leaves state Idle and performs zero repository calls`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, planRepository.getPlansCallCount)
        assertEquals(0, planRepository.getStatusCallCount)
        assertEquals(0, taskRepository.getNextTasksCallCount)
        assertEquals(0, planRepository.syncCallCount)
        assertEquals(0, planRepository.doctorCallCount)
    }

    @Test
    fun `dashboard loads plans, tasks, and plan status successfully`() = runTest {
        val testPlans = listOf(
            Plan(id = "atlas", title = "Project Atlas", taskCount = 10, completedTaskCount = 4, status = "in_progress"),
        )
        val testTasks = listOf(
            Task(id = "task-101", planId = "atlas", title = "Fix parser", status = TaskStatus.IN_PROGRESS, branch = "task-101-branch", headSha = "a".repeat(40)),
        )
        val testStatus = PlanStatus(
            id = "atlas",
            status = "in_progress",
            halted = true,
            openBranches = listOf("task-101-branch", "task-102-branch"),
            blockedTasks = listOf("task-105"),
            currentMilestone = "M2 - Core Engine",
        )

        planRepository.plansResult = AppResult.Success(testPlans)
        taskRepository.nextTasksResult = AppResult.Success(testTasks)
        planRepository.statusResult = { AppResult.Success(testStatus) }

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.refresh()

            val state = awaitItem()
            val success = if (state is UiState.Loading) awaitItem() else state
            assertTrue(success is UiState.Success)
            val data = (success as UiState.Success).data
            assertEquals(1, data.plans.size)
            assertEquals("atlas", data.plans[0].id)
            assertEquals(1, data.nextTasks.size)
            assertEquals("task-101", data.nextTasks[0].id)
            assertTrue(data.isHalted)
            assertEquals(2, data.openBranches.size)
            assertEquals("task-101-branch", data.openBranches[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dashboard emits error when plans fail to load`() = runTest {
        planRepository.plansResult = AppResult.Failure(code = "gateway_offline", message = "Gateway offline")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())
            viewModel.refresh()
            val state = awaitItem()
            val error = if (state is UiState.Loading) awaitItem() else state
            assertTrue(error is UiState.Error)
            assertEquals("Gateway offline", (error as UiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dashboard succeeds with empty status when status fails but plans succeed`() = runTest {
        val testPlans = listOf(
            Plan(id = "atlas", title = "Project Atlas", taskCount = 5, completedTaskCount = 1, status = "active"),
        )
        planRepository.plansResult = AppResult.Success(testPlans)
        taskRepository.nextTasksResult = AppResult.Success(emptyList())
        planRepository.statusResult = { AppResult.Failure(code = "status_error", message = "Status unavailable") }

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())
            viewModel.refresh()
            val state = awaitItem()
            val success = if (state is UiState.Loading) awaitItem() else state
            assertTrue(success is UiState.Success)
            val data = (success as UiState.Success).data
            assertEquals(1, data.plans.size)
            assertFalse(data.isHalted)
            assertTrue(data.openBranches.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh updates dashboard state`() = runTest {
        planRepository.plansResult = AppResult.Failure(code = "error", message = "Initial failure")

        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.refresh()
            val initial = awaitItem()
            val error = if (initial is UiState.Loading) awaitItem() else initial
            assertTrue(error is UiState.Error)

            planRepository.plansResult = AppResult.Success(listOf(Plan("plan-2", "Plan 2", 1, 0, "active")))
            taskRepository.nextTasksResult = AppResult.Success(emptyList())
            planRepository.statusResult = { AppResult.Success(PlanStatus("plan-2", "active", false, emptyList(), emptyList(), "M1")) }

            viewModel.refresh()

            val mid = awaitItem()
            val updated = if (mid is UiState.Loading) awaitItem() else mid
            assertTrue(updated is UiState.Success)
            assertEquals("plan-2", (updated as UiState.Success).data.plans[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncBranch updates diagnosticsState on success and failure`() = runTest {
        val expectedHead = "a".repeat(40)
        planRepository.syncResult = { branch ->
            assertEquals("main", branch)
            AppResult.Success(SyncStatus(status = "up_to_date", headSha = expectedHead, inSync = true))
        }
        val viewModel = createViewModel()

        viewModel.syncBranch("main")
        val afterSuccess = viewModel.diagnosticsState.value
        assertFalse(afterSuccess.isSyncing)
        assertNull(afterSuccess.syncError)
        assertEquals(SyncStatus("up_to_date", expectedHead, true), afterSuccess.syncStatus)
        assertEquals(1, planRepository.syncCallCount)

        planRepository.syncResult = { AppResult.Failure(code = "unreachable", message = "Sync unreachable") }
        viewModel.syncBranch("main")
        val afterFailure = viewModel.diagnosticsState.value
        assertFalse(afterFailure.isSyncing)
        assertEquals("Sync unreachable", afterFailure.syncError)
    }

    @Test
    fun `runDoctor updates diagnosticsState on success and failure`() = runTest {
        val report = DoctorReport(
            healthy = true,
            checks = listOf(DoctorCheck(name = "github_token", passed = true, details = "valid")),
        )
        planRepository.doctorResult = { AppResult.Success(report) }
        val viewModel = createViewModel()

        viewModel.runDoctor()
        val afterSuccess = viewModel.diagnosticsState.value
        assertFalse(afterSuccess.isRunningDoctor)
        assertNull(afterSuccess.doctorError)
        assertEquals(report, afterSuccess.doctorReport)
        assertEquals(1, planRepository.doctorCallCount)

        planRepository.doctorResult = { AppResult.Failure(code = "halt_active", message = "Engine halted") }
        viewModel.runDoctor()
        val afterFailure = viewModel.diagnosticsState.value
        assertFalse(afterFailure.isRunningDoctor)
        assertEquals("Engine halted", afterFailure.doctorError)
    }
}
