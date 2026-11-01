package com.inscopelabs.buildengine.ui.plans

import app.cash.turbine.test
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.read.RenderCheckUseCase
import com.inscopelabs.buildengine.domain.usecase.read.ValidatePlanUseCase
import com.inscopelabs.buildengine.testutil.FakePlanRepository
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var planRepository: FakePlanRepository
    private lateinit var getPlansUseCase: GetPlansUseCase
    private lateinit var getStatusUseCase: GetStatusUseCase
    private lateinit var validatePlanUseCase: ValidatePlanUseCase
    private lateinit var renderCheckUseCase: RenderCheckUseCase

    @Before
    fun setUp() {
        planRepository = FakePlanRepository()
        getPlansUseCase = GetPlansUseCase(planRepository)
        getStatusUseCase = GetStatusUseCase(planRepository)
        validatePlanUseCase = ValidatePlanUseCase(planRepository)
        renderCheckUseCase = RenderCheckUseCase(planRepository)
    }

    @Test
    fun `planListState transitions to Success when plans load`() = runTest {
        val testPlans = listOf(
            Plan(id = "p1", title = "Plan 1", taskCount = 3, completedTaskCount = 1, status = "active"),
        )
        planRepository.plansResult = AppResult.Success(testPlans)

        val viewModel = PlanViewModel(
            getPlansUseCase = getPlansUseCase,
            planRepository = planRepository,
            getStatusUseCase = getStatusUseCase,
            validatePlanUseCase = validatePlanUseCase,
            renderCheckUseCase = renderCheckUseCase,
        )

        viewModel.planListState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            assertEquals(1, (state as UiState.Success).data.size)
            assertEquals("p1", state.data[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `planListState transitions to Error when plans fail`() = runTest {
        planRepository.plansResult = AppResult.Failure(code = "network_error", message = "Network error")

        val viewModel = PlanViewModel(
            getPlansUseCase = getPlansUseCase,
            planRepository = planRepository,
            getStatusUseCase = getStatusUseCase,
            validatePlanUseCase = validatePlanUseCase,
            renderCheckUseCase = renderCheckUseCase,
        )

        viewModel.planListState.test {
            val state = awaitItem()
            assertTrue(state is UiState.Error)
            assertEquals("Network error", (state as UiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPlanDetail loads plan and status successfully`() = runTest {
        val testPlan = Plan(id = "atlas", title = "Project Atlas", taskCount = 5, completedTaskCount = 2, status = "in_progress")
        val testStatus = PlanStatus(id = "atlas", status = "in_progress", halted = false, openBranches = listOf("b1"), blockedTasks = emptyList(), currentMilestone = "M1")

        planRepository.singlePlanResult = { AppResult.Success(testPlan) }
        planRepository.statusResult = { AppResult.Success(testStatus) }

        val viewModel = PlanViewModel(
            getPlansUseCase = getPlansUseCase,
            planRepository = planRepository,
            getStatusUseCase = getStatusUseCase,
            validatePlanUseCase = validatePlanUseCase,
            renderCheckUseCase = renderCheckUseCase,
        )

        viewModel.planDetailState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadPlanDetail("atlas")

            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val data = (state as UiState.Success).data
            assertEquals("atlas", data.plan.id)
            assertEquals("Project Atlas", data.plan.title)
            assertNotNull(data.status)
            assertEquals("M1", data.status?.currentMilestone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadPlanDetail emits Error when plan is not found`() = runTest {
        planRepository.singlePlanResult = { AppResult.Failure(code = "not_found", message = "Plan not found: missing") }

        val viewModel = PlanViewModel(
            getPlansUseCase = getPlansUseCase,
            planRepository = planRepository,
            getStatusUseCase = getStatusUseCase,
            validatePlanUseCase = validatePlanUseCase,
            renderCheckUseCase = renderCheckUseCase,
        )

        viewModel.planDetailState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadPlanDetail("missing")

            val state = awaitItem()
            assertTrue(state is UiState.Error)
            assertEquals("Plan not found: missing", (state as UiState.Error).message)
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

        val viewModel = PlanViewModel(
            getPlansUseCase = getPlansUseCase,
            planRepository = planRepository,
            getStatusUseCase = getStatusUseCase,
            validatePlanUseCase = validatePlanUseCase,
            renderCheckUseCase = renderCheckUseCase,
        )

        viewModel.planDetailState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadPlanDetail("atlas")
            val state = awaitItem()
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
