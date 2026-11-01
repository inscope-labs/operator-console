package com.inscopelabs.buildengine.ui.taskdetail

import app.cash.turbine.test
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var viewModel: TaskDetailViewModel

    @Before
    fun setUp() {
        taskRepository = FakeTaskRepository()
        viewModel = TaskDetailViewModel(taskRepository)
    }

    @Test
    fun `loadTask transitions to Success with task and branch head`() = runTest {
        val testTask = Task(
            id = "task-101",
            planId = "atlas",
            title = "Implement parser",
            status = TaskStatus.IN_PROGRESS,
            branch = "task-101-parser",
            headSha = "0".repeat(40),
        )
        val testBranch = TaskBranch(
            name = "task-101-parser",
            headSha = "f".repeat(40),
            guardsPassed = true,
        )

        taskRepository.singleTaskResult = { AppResult.Success(testTask) }
        taskRepository.branchResult = { AppResult.Success(testBranch) }

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadTask("task-101")

            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val data = (state as UiState.Success).data
            assertEquals("task-101", data.task.id)
            assertEquals("Implement parser", data.task.title)
            assertNotNull(data.branch)
            assertEquals("f".repeat(40), data.branch?.headSha)
            assertEquals(true, data.branch?.guardsPassed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadTask emits Error when task is not found`() = runTest {
        taskRepository.singleTaskResult = { AppResult.Failure(code = "not_found", message = "Task not found: missing") }

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadTask("missing")

            val state = awaitItem()
            assertTrue(state is UiState.Error)
            assertEquals("Task not found: missing", (state as UiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadTask succeeds with null branch when branchFor returns failure`() = runTest {
        val testTask = Task(
            id = "task-102",
            planId = "atlas",
            title = "Task without branch head",
            status = TaskStatus.PENDING,
            branch = "task-102-branch",
            headSha = "1".repeat(40),
        )

        taskRepository.singleTaskResult = { AppResult.Success(testTask) }
        taskRepository.branchResult = { AppResult.Failure(code = "not_found", message = "Branch not found") }

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.loadTask("task-102")

            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val data = (state as UiState.Success).data
            assertEquals("task-102", data.task.id)
            assertNull(data.branch)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
