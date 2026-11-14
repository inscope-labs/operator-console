package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionRequestDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusResult
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusTarget
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusUseCase
import com.inscopelabs.buildengine.testutil.FakePlanRepository
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.RecordingStage34OperationRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.4 (T3-404 / PR3-026): SetTaskStatusUseCase transition matrix, completion evidence,
 * schema compliance, and authoritative state refresh.
 */
class SetTaskStatusUseCaseTest {

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )
    private val branch = "task-10008-refactor-parser"
    private val head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"

    private var refreshedTaskStatus = TaskStatus.IN_PROGRESS
    private val taskRepository = FakeTaskRepository().apply {
        liveHeadResult = { AppResult.Success(head) }
        branchResult = { AppResult.Success(TaskBranch(name = branch, headSha = head)) }
        singleTaskResult = { taskId ->
            AppResult.Success(
                Task(
                    id = taskId,
                    planId = "atlas",
                    title = "Refactor parser",
                    status = refreshedTaskStatus,
                    branch = branch,
                    headSha = head,
                ),
            )
        }
    }
    private val planRepository = FakePlanRepository().apply {
        statusResult = { planId ->
            AppResult.Success(PlanStatus(planId, "active", false, emptyList(), emptyList(), "M3"))
        }
    }
    private var prompts = 0
    private val biometricGate = BiometricGate { _, _ ->
        prompts++
        true
    }
    private val operationRepository = RecordingStage34OperationRepository(
        defaultOutcome = OperationOutcome.Succeeded(auditRef = "audit-status-1", headSha = head, outcome = "in_progress"),
    )

    private fun useCase() = SetTaskStatusUseCase(
        operationRepository = operationRepository,
        taskRepository = taskRepository,
        confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
            clock = clock,
        ),
        nonceSource = nonceSource,
        planRepository = planRepository,
    )

    private fun baseTarget(
        current: TaskStatus = TaskStatus.PENDING,
        target: TaskStatus = TaskStatus.IN_PROGRESS,
        evidence: String? = null,
    ) = SetTaskStatusTarget(
        taskId = "10008",
        plan = "atlas",
        order = "order-7731",
        branch = branch,
        headSha = head,
        currentStatus = current,
        targetStatus = target,
        evidence = evidence,
    )

    @Test
    fun `invalid transitions are rejected before prompt or send`() = runTest {
        val invalidPairs = listOf(
            TaskStatus.PENDING to TaskStatus.PENDING,
            TaskStatus.PENDING to TaskStatus.UNKNOWN,
            TaskStatus.IN_PROGRESS to TaskStatus.PENDING,
            TaskStatus.IN_PROGRESS to TaskStatus.IN_PROGRESS,
            TaskStatus.IN_PROGRESS to TaskStatus.UNKNOWN,
            TaskStatus.DONE to TaskStatus.PENDING,
            TaskStatus.DONE to TaskStatus.IN_PROGRESS,
            TaskStatus.DONE to TaskStatus.DONE,
            TaskStatus.UNKNOWN to TaskStatus.IN_PROGRESS,
            TaskStatus.UNKNOWN to TaskStatus.DONE,
        )
        for ((from, to) in invalidPairs) {
            val res = useCase()(baseTarget(current = from, target = to, evidence = "CI run #88 passed"))
            assertTrue("Expected InvalidTransition for $from -> $to, got $res", res is SetTaskStatusResult.InvalidTransition)
        }
        assertEquals(0, prompts)
        assertTrue(operationRepository.statusTransitions.isEmpty())
    }

    @Test
    fun `transitioning to DONE requires non-blank completion evidence before prompt or send`() = runTest {
        val missing = useCase()(baseTarget(current = TaskStatus.IN_PROGRESS, target = TaskStatus.DONE, evidence = null))
        val blank = useCase()(baseTarget(current = TaskStatus.PENDING, target = TaskStatus.DONE, evidence = "   "))

        assertTrue(missing is SetTaskStatusResult.MissingEvidence)
        assertTrue(blank is SetTaskStatusResult.MissingEvidence)
        assertEquals(0, prompts)
        assertTrue(operationRepository.statusTransitions.isEmpty())
    }

    @Test
    fun `valid PENDING to IN_PROGRESS and IN_PROGRESS to DONE mint scoped capability and refresh authoritative state`() = runTest {
        refreshedTaskStatus = TaskStatus.IN_PROGRESS
        val inProgress = useCase()(baseTarget(current = TaskStatus.PENDING, target = TaskStatus.IN_PROGRESS))
            as SetTaskStatusResult.Executed
        assertTrue(inProgress.outcome is OperationOutcome.Succeeded)
        assertEquals(TaskStatus.IN_PROGRESS, inProgress.authoritativeTask?.status)
        assertEquals("in_progress", operationRepository.statusTransitions[0].second)
        assertEquals(OperationType.SET_IN_PROGRESS, operationRepository.statusTransitions[0].first.op)

        refreshedTaskStatus = TaskStatus.DONE
        operationRepository.defaultOutcome = OperationOutcome.Succeeded(
            auditRef = "audit-status-2",
            headSha = head,
            outcome = "completed",
        )
        val done = useCase()(
            baseTarget(current = TaskStatus.IN_PROGRESS, target = TaskStatus.DONE, evidence = "CI run #412 green"),
        ) as SetTaskStatusResult.Executed
        assertEquals(TaskStatus.DONE, done.authoritativeTask?.status)
        assertEquals("completed", operationRepository.statusTransitions[1].second)
        assertEquals(OperationType.SET_DONE, operationRepository.statusTransitions[1].first.op)
        assertEquals(2, planRepository.getStatusCallCount)
    }

    @Test
    fun `refused transition skips authoritative refresh`() = runTest {
        operationRepository.defaultOutcome = OperationOutcome.Refused(code = "HALT_ACTIVE")
        val res = useCase()(baseTarget(current = TaskStatus.PENDING, target = TaskStatus.IN_PROGRESS))
            as SetTaskStatusResult.Executed
        assertNull(res.authoritativeTask)
        assertNull(res.authoritativePlanStatus)
        assertEquals(0, planRepository.getStatusCallCount)
    }

    @Test
    fun `PlanStatusTransitionRequestDto serializes strictly to plan-status-transition schema keys`() {
        val dto = PlanStatusTransitionRequestDto(
            plan = "atlas",
            task = "10008",
            branch = branch,
            expectedHead = head,
            targetStatus = "completed",
            idempotencyKey = "a".repeat(32),
            capability = "cap-wire",
        )
        val keys = Json.parseToJsonElement(Json.encodeToString(dto)).jsonObject.keys
        assertEquals(
            setOf("plan", "task", "branch", "expectedHead", "targetStatus", "idempotencyKey", "capability"),
            keys,
        )
    }
}
