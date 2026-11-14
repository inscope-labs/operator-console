package com.inscopelabs.buildengine.domain.usecase

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.PublishResult
import com.inscopelabs.buildengine.domain.usecase.write.PublishTarget
import com.inscopelabs.buildengine.domain.usecase.write.PublishTaskUseCase
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 3.3 (T3-301, T3-303): the publish use case.
 *
 * Covers the confirmation path (head drift, unavailable head, declined confirmation),
 * the guarantee that nothing is sent unless a capability was created, a fresh single-use
 * nonce per attempt, the capability lifetime, and that every executor outcome reaches the
 * caller unchanged.
 */
class PublishTaskUseCaseTest {

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )

    private val branch = "task-10008-refactor-parser"
    private val head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
    private val target = PublishTarget(
        taskId = "10008",
        plan = "atlas",
        order = "order-7731",
        branch = branch,
        headSha = head,
    )

    private val taskRepository = FakeTaskRepository().apply {
        branchResult = { AppResult.Success(TaskBranch(name = branch, headSha = head)) }
    }
    private var prompts = 0
    private var promptAnswer = true
    private val biometricGate = BiometricGate(
        authenticator = { _, _ ->
            prompts++
            promptAnswer
        },
    )
    private val repository = RecordingRepository(
        OperationOutcome.Succeeded(auditRef = "audit-1", headSha = head),
    )

    private fun useCase() = PublishTaskUseCase(
        operationRepository = repository,
        confirmAndMintCapability = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = biometricGate,
            verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
            clock = clock,
        ),
        nonceSource = nonceSource,
    )

    @Test
    fun `confirmed publish sends exactly one request bound to the branch head and order`() = runTest {
        val result = useCase()(target)

        assertEquals(PublishResult.Executed(repository.outcome), result)
        assertEquals(1, prompts)
        val (request, capability) = repository.published.single()
        assertEquals(OperationType.PUBLISH, request.op)
        assertEquals(branch, request.scope.branch)
        assertEquals(head, request.scope.head)
        assertEquals("atlas", request.plan)
        assertEquals("10008", request.task)
        assertEquals("order-7731", request.order)
        assertEquals(OperationType.PUBLISH, capability.capability.op)
        assertEquals(branch, capability.capability.branch)
        assertEquals(head, capability.capability.head)
        assertEquals("order-7731", capability.capability.order)
    }

    @Test
    fun `capability lifetime starts at approval`() = runTest {
        useCase()(target)
        val (_, capability) = repository.published.single()
        assertEquals(1700000000L + minter.ttlSeconds, capability.capability.exp)
    }

    @Test
    fun `every attempt uses a fresh single-use nonce`() = runTest {
        useCase()(target)
        useCase()(target)
        val nonces = repository.published.map { it.second.capability.nonce }
        assertEquals(2, nonces.size)
        assertNotEquals(nonces[0], nonces[1])
    }

    @Test
    fun `a drifted head refuses before any prompt or send`() = runTest {
        val drifted = "b".repeat(40)
        taskRepository.branchResult = { AppResult.Success(TaskBranch(name = branch, headSha = drifted)) }

        val result = useCase()(target)

        assertEquals(PublishResult.NotAuthorized(MintResult.HeadDrifted(drifted)), result)
        assertEquals(0, prompts)
        assertTrue(repository.published.isEmpty())
    }

    @Test
    fun `an unavailable head check refuses before any prompt or send`() = runTest {
        taskRepository.branchResult = { AppResult.Failure(code = "unreachable", message = "no route") }

        val result = useCase()(target)

        assertTrue(result is PublishResult.NotAuthorized)
        assertTrue((result as PublishResult.NotAuthorized).reason is MintResult.HeadUnavailable)
        assertEquals(0, prompts)
        assertTrue(repository.published.isEmpty())
    }

    @Test
    fun `a declined confirmation sends nothing`() = runTest {
        promptAnswer = false

        val result = useCase()(target)

        assertEquals(PublishResult.NotAuthorized(MintResult.Declined), result)
        assertEquals(1, prompts)
        assertTrue(repository.published.isEmpty())
    }

    @Test
    fun `an invalid target is rejected without a prompt or a send`() = runTest {
        val badBranch = useCase()(target.copy(branch = "main"))
        val blankOrder = useCase()(target.copy(order = " "))
        val shortHead = useCase()(target.copy(headSha = "abc123"))

        assertTrue(badBranch is PublishResult.InvalidTarget)
        assertTrue(blankOrder is PublishResult.InvalidTarget)
        assertTrue(shortHead is PublishResult.InvalidTarget)
        assertEquals(0, prompts)
        assertTrue(repository.published.isEmpty())
    }

    @Test
    fun `each executor outcome reaches the caller unchanged and is never upgraded to success`() = runTest {
        val outcomes = listOf(
            OperationOutcome.Refused(code = "HALT_ACTIVE"),
            OperationOutcome.Refused(code = "BRANCH_MISMATCH"),
            OperationOutcome.Conflict(code = "NONCE_REPLAYED"),
            OperationOutcome.Conflict(code = "HEAD_DRIFT"),
            OperationOutcome.Timeout(idempotencyKey = "a".repeat(32)),
            OperationOutcome.UncertainCompletion(idempotencyKey = "b".repeat(32)),
            OperationOutcome.RecoveryRequired(idempotencyKey = "c".repeat(32)),
        )
        for (outcome in outcomes) {
            repository.outcome = outcome
            assertEquals(PublishResult.Executed(outcome), useCase()(target))
        }
    }

    private class RecordingRepository(var outcome: OperationOutcome) : OperationRepository {
        val published = mutableListOf<Pair<OperationRequest, SignedCapability>>()

        override suspend fun publish(request: OperationRequest, capability: SignedCapability): OperationOutcome {
            published += request to capability
            return outcome
        }

        override suspend fun upload(request: OperationRequest): OperationResult = error("not used")
        override suspend fun reconcile(request: OperationRequest): OperationResult = error("not used")
        override suspend fun halt(request: OperationRequest): OperationResult = error("not used")
        override suspend fun resume(request: OperationRequest): OperationResult = error("not used")
        override suspend fun decide(request: OperationRequest): OperationResult = error("not used")
    }
}
