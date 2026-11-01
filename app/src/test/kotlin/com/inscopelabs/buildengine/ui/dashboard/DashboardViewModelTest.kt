package com.inscopelabs.buildengine.ui.dashboard

import app.cash.turbine.test
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.usecase.read.GetNextTasksUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.testutil.FakePlanRepository
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Before
    fun setUp() {
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository()
        getPlansUseCase = GetPlansUseCase(planRepository)
        getNextTasksUseCase = GetNextTasksUseCase(taskRepository)
        getStatusUseCase = GetStatusUseCase(planRepository)
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

        val viewModel = DashboardViewModel(
            getPlansUseCase = getPlansUseCase,
            getNextTasksUseCase = getNextTasksUseCase,
            getStatusUseCase = getStatusUseCase,
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val data = (state as UiState.Success).data
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

        val viewModel = DashboardViewModel(
            getPlansUseCase = getPlansUseCase,
            getNextTasksUseCase = getNextTasksUseCase,
            getStatusUseCase = getStatusUseCase,
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Error)
            assertEquals("Gateway offline", (state as UiState.Error).message)
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

        val viewModel = DashboardViewModel(
            getPlansUseCase = getPlansUseCase,
            getNextTasksUseCase = getNextTasksUseCase,
            getStatusUseCase = getStatusUseCase,
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val data = (state as UiState.Success).data
            assertEquals(1, data.plans.size)
            assertFalse(data.isHalted)
            assertTrue(data.openBranches.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh updates dashboard state`() = runTest {
        planRepository.plansResult = AppResult.Failure(code = "error", message = "Initial failure")

        val viewModel = DashboardViewModel(
            getPlansUseCase = getPlansUseCase,
            getNextTasksUseCase = getNextTasksUseCase,
            getStatusUseCase = getStatusUseCase,
        )

        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial is UiState.Error)

            // Now recover
            planRepository.plansResult = AppResult.Success(listOf(Plan("plan-2", "Plan 2", 1, 0, "active")))
            taskRepository.nextTasksResult = AppResult.Success(emptyList())
            planRepository.statusResult = { AppResult.Success(PlanStatus("plan-2", "active", false, emptyList(), emptyList(), "M1")) }

            viewModel.refresh()

            val updated = awaitItem()
            assertTrue(updated is UiState.Success)
            assertEquals("plan-2", (updated as UiState.Success).data.plans[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
