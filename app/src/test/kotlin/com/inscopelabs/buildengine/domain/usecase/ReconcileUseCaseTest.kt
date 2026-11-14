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
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.ReconcileDisposition
import com.inscopelabs.buildengine.domain.usecase.write.ReconcileResult
import com.inscopelabs.buildengine.domain.usecase.write.ReconcileTarget
import com.inscopelabs.buildengine.domain.usecase.write.ReconcileUseCase
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.RecordingStage34OperationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.4 (T3-402 / PR3-023): ReconcileUseCase scope binding and disposition parsing.
 */
class ReconcileUseCaseTest {

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )
    private val branch = "task-10008-refactor-parser"
    private val head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
    private val target = ReconcileTarget(
        taskId = "10008",
        plan = "atlas",
        order = "order-7731",
        branch = branch,
        headSha = head,
        submissionId = "sub-42",
    )

    private val taskRepository = FakeTaskRepository().apply {
        liveHeadResult = { AppResult.Success(head) }
        branchResult = { AppResult.Success(TaskBranch(name = branch, headSha = head)) }
    }
    private var prompts = 0
    private val biometricGate = BiometricGate { _, _ ->
        prompts++
        true
    }
    private val operationRepository = RecordingStage34OperationRepository()

    private fun useCase() = ReconcileUseCase(
        operationRepository = operationRepository,
        confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
            clock = clock,
        ),
        nonceSource = nonceSource,
    )

    @Test
    fun `confirmed reconcile parses MERGED and CLOSED outcomes from gateway`() = runTest {
        operationRepository.defaultOutcome = OperationOutcome.Succeeded(
            auditRef = "audit-rec-1",
            headSha = head,
            outcome = "MERGED: pr #42",
        )
        val merged = useCase()(target) as ReconcileResult.Executed
        assertEquals(ReconcileDisposition.Merged("MERGED: pr #42"), merged.disposition)

        operationRepository.defaultOutcome = OperationOutcome.Succeeded(
            auditRef = "audit-rec-2",
            headSha = head,
            outcome = "CLOSED: superseded by rev-2",
        )
        val closed = useCase()(target) as ReconcileResult.Executed
        assertEquals(ReconcileDisposition.Closed("CLOSED: superseded by rev-2"), closed.disposition)

        val (request, submissionId, cap) = operationRepository.reconciles.first()
        assertEquals(OperationType.RECONCILE, request.op)
        assertEquals("sub-42", submissionId)
        assertEquals(OperationType.RECONCILE, cap.capability.op)
        assertEquals(branch, cap.capability.branch)
        assertEquals(head, cap.capability.head)
    }

    @Test
    fun `non-success reconcile outcomes propagate without disposition or success upgrade`() = runTest {
        val nonSuccess = listOf(
            OperationOutcome.Refused(code = "HALT_ACTIVE"),
            OperationOutcome.Conflict(code = "HEAD_DRIFT"),
            OperationOutcome.Timeout(idempotencyKey = "a".repeat(32)),
            OperationOutcome.UncertainCompletion(idempotencyKey = "b".repeat(32)),
            OperationOutcome.RecoveryRequired(idempotencyKey = "c".repeat(32)),
        )
        for (outcome in nonSuccess) {
            operationRepository.defaultOutcome = outcome
            val result = useCase()(target) as ReconcileResult.Executed
            assertEquals(outcome, result.outcome)
            assertNull(result.disposition)
        }
    }

    @Test
    fun `invalid submissionId or non-task branch fails before prompt or send`() = runTest {
        val blankSubmission = useCase()(target.copy(submissionId = "  "))
        val mainBranch = useCase()(target.copy(branch = "main"))

        assertTrue(blankSubmission is ReconcileResult.InvalidTarget)
        assertTrue(mainBranch is ReconcileResult.InvalidTarget)
        assertEquals(0, prompts)
        assertTrue(operationRepository.reconciles.isEmpty())
    }

    @Test
    fun `head drift refuses reconcile before prompt or send`() = runTest {
        val drifted = "e".repeat(40)
        taskRepository.liveHeadResult = { AppResult.Success(drifted) }

        val result = useCase()(target)

        assertEquals(ReconcileResult.NotAuthorized(MintResult.HeadDrifted(drifted)), result)
        assertEquals(0, prompts)
        assertTrue(operationRepository.reconciles.isEmpty())
    }
}
