package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.HaltResult
import com.inscopelabs.buildengine.domain.usecase.write.HaltTarget
import com.inscopelabs.buildengine.domain.usecase.write.HaltUseCase
import com.inscopelabs.buildengine.domain.usecase.write.ResumeResult
import com.inscopelabs.buildengine.domain.usecase.write.ResumeTarget
import com.inscopelabs.buildengine.domain.usecase.write.ResumeUseCase
import com.inscopelabs.buildengine.testutil.FakePlanRepository
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.RecordingStage34OperationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.4 (T3-401 / PR3-024): Halt & Resume use-case guarantees.
 */
class HaltAndResumeUseCaseTest {

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )
    private val engineHead = "c".repeat(40)
    private val taskRepository = FakeTaskRepository().apply {
        liveHeadResult = { branch -> AppResult.Success(if (branch == "engine") engineHead else "d".repeat(40)) }
        branchResult = { AppResult.Success(TaskBranch(name = "engine", headSha = engineHead)) }
    }
    private val planRepository = FakePlanRepository().apply {
        statusResult = { planId ->
            AppResult.Success(
                PlanStatus(
                    id = planId,
                    status = "active",
                    halted = currentHaltedState,
                    openBranches = emptyList(),
                    blockedTasks = emptyList(),
                    currentMilestone = "M3",
                ),
            )
        }
    }
    private var currentHaltedState = false
    private var prompts = 0
    private var promptAnswer = true
    private val biometricGate = BiometricGate { _, _ ->
        prompts++
        promptAnswer
    }
    private val operationRepository = RecordingStage34OperationRepository(
        defaultOutcome = OperationOutcome.Succeeded(auditRef = "audit-halt-1", headSha = engineHead),
    )

    private fun confirmAndMint() = ConfirmAndMintCapabilityUseCase(
        capabilityMinter = minter,
        biometricGate = biometricGate,
        verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
        clock = clock,
    )

    private fun haltUseCase() = HaltUseCase(
        operationRepository = operationRepository,
        confirmAndMintCapability = confirmAndMint(),
        nonceSource = nonceSource,
        planRepository = planRepository,
    )

    private fun resumeUseCase() = ResumeUseCase(
        operationRepository = operationRepository,
        confirmAndMintCapability = confirmAndMint(),
        nonceSource = nonceSource,
        planRepository = planRepository,
    )

    @Test
    fun `halt mints engine-scoped capability, trims reason, and queries authoritative halted status`() = runTest {
        currentHaltedState = true
        val result = haltUseCase()(
            HaltTarget(
                plan = "atlas",
                order = "order-1",
                headSha = engineHead,
                reason = "  CI detected nondeterministic build  ",
            ),
        )

        assertTrue(result is HaltResult.Executed)
        val executed = result as HaltResult.Executed
        assertTrue(executed.outcome is OperationOutcome.Succeeded)
        assertEquals(true, executed.authoritativeHalted)
        assertEquals(1, prompts)
        assertEquals(1, planRepository.getStatusCallCount)

        val (request, reason, cap) = operationRepository.halts.single()
        assertEquals(OperationType.HALT, request.op)
        assertEquals("engine", request.scope.branch)
        assertEquals("CI detected nondeterministic build", reason)
        assertEquals(OperationType.HALT, cap.capability.op)
        assertEquals("engine", cap.capability.branch)
        assertEquals(engineHead, cap.capability.head)
    }

    @Test
    fun `halt rejects task-scoped branch, blank reason, and forbidden secret text before prompt or send`() = runTest {
        val taskBranch = haltUseCase()(
            HaltTarget(
                plan = "atlas",
                order = "order-1",
                headSha = engineHead,
                reason = "operator stop",
                branch = "task-10008-foo",
            ),
        )
        val blankReason = haltUseCase()(
            HaltTarget(
                plan = "atlas",
                order = "order-1",
                headSha = engineHead,
                reason = "   ",
            ),
        )
        val secretReason = haltUseCase()(
            HaltTarget(
                plan = "atlas",
                order = "order-1",
                headSha = engineHead,
                reason = "Authorization: Bearer supersecret",
            ),
        )

        assertTrue(taskBranch is HaltResult.InvalidTarget)
        assertTrue(blankReason is HaltResult.InvalidTarget)
        assertTrue(secretReason is HaltResult.InvalidTarget)
        assertEquals(0, prompts)
        assertTrue(operationRepository.halts.isEmpty())
    }

    @Test
    fun `resume refuses before prompt or send when no recorded halt cause exists in journal`() = runTest {
        operationRepository.haltCause = null

        val result = resumeUseCase()(
            ResumeTarget(
                plan = "atlas",
                order = "order-1",
                headSha = engineHead,
            ),
        )

        assertTrue(result is ResumeResult.MissingRecordedCause)
        assertEquals(0, prompts)
        assertTrue(operationRepository.resumes.isEmpty())
    }

    @Test
    fun `resume uses recorded halt cause, rejects task branch, and queries authoritative state`() = runTest {
        val taskBranch = resumeUseCase()(
            ResumeTarget(
                plan = "atlas",
                order = "order-1",
                headSha = engineHead,
                branch = "task-10008-foo",
            ),
        )
        assertTrue(taskBranch is ResumeResult.InvalidTarget)
        assertEquals(0, prompts)

        operationRepository.haltCause = "Incident #42 mitigated"
        currentHaltedState = false
        val result = resumeUseCase()(
            ResumeTarget(
                plan = "atlas",
                order = "order-1",
                headSha = engineHead,
            ),
        )

        assertTrue(result is ResumeResult.Executed)
        val executed = result as ResumeResult.Executed
        assertTrue(executed.outcome is OperationOutcome.Succeeded)
        assertEquals(false, executed.authoritativeHalted)
        val (request, reason, cap) = operationRepository.resumes.single()
        assertEquals(OperationType.RESUME, request.op)
        assertEquals("engine", request.scope.branch)
        assertEquals("Incident #42 mitigated", reason)
        assertEquals(OperationType.RESUME, cap.capability.op)
        assertEquals("engine", cap.capability.branch)
    }

    @Test
    fun `declined confirmation for halt or resume sends nothing`() = runTest {
        promptAnswer = false
        operationRepository.haltCause = "halted earlier"

        val haltResult = haltUseCase()(HaltTarget("atlas", "order-1", engineHead, "maintenance"))
        val resumeResult = resumeUseCase()(ResumeTarget("atlas", "order-1", engineHead))

        assertEquals(HaltResult.NotAuthorized(MintResult.Declined), haltResult)
        assertEquals(ResumeResult.NotAuthorized(MintResult.Declined), resumeResult)
        assertTrue(operationRepository.halts.isEmpty())
        assertTrue(operationRepository.resumes.isEmpty())
    }
}
