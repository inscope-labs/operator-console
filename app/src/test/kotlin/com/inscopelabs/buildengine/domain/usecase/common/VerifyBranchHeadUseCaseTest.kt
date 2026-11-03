package com.inscopelabs.buildengine.domain.usecase.common

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyBranchHeadUseCaseTest {

    private val taskRepository = FakeTaskRepository()
    private val useCase = VerifyBranchHeadUseCase(taskRepository)

    @Test
    fun `returns Match when live head matches expected head exactly`() = runTest {
        val head = "a".repeat(40)
        taskRepository.branchResult = { taskId ->
            AppResult.Success(TaskBranch(name = "task-$taskId", headSha = head))
        }

        val result = useCase("t1", head)
        assertEquals(VerifyBranchHeadResult.Match, result)
    }

    @Test
    fun `returns Match when live head matches expected head case-insensitively`() = runTest {
        val headLower = "abcdef0123456789abcdef0123456789abcdef01"
        val headUpper = headLower.uppercase()
        taskRepository.branchResult = { taskId ->
            AppResult.Success(TaskBranch(name = "task-$taskId", headSha = headLower))
        }

        val result = useCase("t1", headUpper)
        assertEquals(VerifyBranchHeadResult.Match, result)
    }

    @Test
    fun `returns Drifted when live head has drifted`() = runTest {
        val expectedHead = "0".repeat(40)
        val driftedHead = "1".repeat(40)
        taskRepository.branchResult = { taskId ->
            AppResult.Success(TaskBranch(name = "task-$taskId", headSha = driftedHead))
        }

        val result = useCase("t1", expectedHead)
        assertTrue(result is VerifyBranchHeadResult.Drifted)
        assertEquals(driftedHead, (result as VerifyBranchHeadResult.Drifted).liveSha)
    }

    @Test
    fun `returns Unavailable when repository returns failure`() = runTest {
        taskRepository.branchResult = { taskId ->
            AppResult.Failure(code = "not_found", message = "Task branch missing")
        }

        val result = useCase("t1", "0".repeat(40))
        assertTrue(result is VerifyBranchHeadResult.Unavailable)
        assertEquals("Task branch missing", (result as VerifyBranchHeadResult.Unavailable).reason)
    }

    @Test
    fun `returns Match when liveHead directly matches branch`() = runTest {
        val branch = "task-feature"
        val head = "b".repeat(40)
        taskRepository.liveHeadResult = { b ->
            if (b == branch) AppResult.Success(head) else AppResult.Failure("not_found", "no branch")
        }

        val result = useCase(branch, head)
        assertEquals(VerifyBranchHeadResult.Match, result)
    }

    @Test
    fun `returns Unavailable on blank taskId or blank expectedHead`() = runTest {
        assertTrue(useCase("", "0".repeat(40)) is VerifyBranchHeadResult.Unavailable)
        assertTrue(useCase("t1", "") is VerifyBranchHeadResult.Unavailable)
        assertTrue(useCase("   ", "   ") is VerifyBranchHeadResult.Unavailable)
    }
}
