package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.FakeAuthorityPort
import com.inscopelabs.buildengine.manualpipeline.FakeBranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.FakeMintingPort
import com.inscopelabs.buildengine.manualpipeline.FakeTaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.Fixtures
import com.inscopelabs.buildengine.manualpipeline.RecordingOperatorActionStore
import com.inscopelabs.buildengine.manualpipeline.RecordingStatusTransitionPort
import com.inscopelabs.buildengine.manualpipeline.RecordingWorkflowRecordStore
import com.inscopelabs.buildengine.manualpipeline.StrictTestRedactor
import com.inscopelabs.buildengine.manualpipeline.FixedTestClock
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.RecordOperatorActionUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.RecordWorkflowMetadataUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.RequestTransitionUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ResolveAuthorityUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.validation.EnvironmentIsolationValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HaltStateValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.TransitionPreconditionValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch tests for the manual pipeline's lifecycle controller (MP-003).
 *
 * These are the load-bearing guarantees of the module: one transition per operator action,
 * nothing minted or sent without authority, no blind retry, and no false success.
 */
class RequestTransitionUseCaseTest {

    private val authorityPort = FakeAuthorityPort(Fixtures.authority())
    private val taskGatewayPort = FakeTaskGatewayPort()
    private val branchHeadPort = FakeBranchHeadPort()
    private val mintingPort = FakeMintingPort(MintOutcome.Minted(Fixtures.mintedHandle()))
    private val transitionPort = RecordingStatusTransitionPort()
    private val actionStore = RecordingOperatorActionStore()
    private val workflowStore = RecordingWorkflowRecordStore()

    private fun useCase(
        authority: FakeAuthorityPort = this.authorityPort,
        gateway: FakeTaskGatewayPort = this.taskGatewayPort,
        branchHead: FakeBranchHeadPort = this.branchHeadPort,
        minting: FakeMintingPort = this.mintingPort,
        transitions: RecordingStatusTransitionPort = this.transitionPort,
    ): RequestTransitionUseCase = RequestTransitionUseCase(
        resolveAuthorityUseCase = ResolveAuthorityUseCase(
            authorityPort = authority,
            environmentIsolationValidator = EnvironmentIsolationValidator(),
        ),
        transitionPreconditionValidator = TransitionPreconditionValidator(),
        haltStateValidator = HaltStateValidator(),
        headFreshnessValidator = HeadFreshnessValidator(),
        taskGatewayPort = gateway,
        branchHeadPort = branchHead,
        mintingPort = minting,
        statusTransitionPort = transitions,
        recordWorkflowMetadataUseCase = RecordWorkflowMetadataUseCase(
            store = workflowStore,
            redactor = StrictTestRedactor(),
            clock = FixedTestClock(),
        ),
        recordOperatorActionUseCase = RecordOperatorActionUseCase(
            store = actionStore,
            redactor = StrictTestRedactor(),
            clock = FixedTestClock(),
        ),
    )

    // -- the happy path --

    @Test
    fun `a confirmed transition mints once and dispatches exactly one write`() = runTest {
        val result = useCase()(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.Executed)
        assertEquals(1, mintingPort.transitionMints)
        assertEquals(1, transitionPort.dispatches)
    }

    @Test
    fun `the dispatched request is exactly the one the operator confirmed`() = runTest {
        val request = Fixtures.transitionRequest()
        useCase()(Fixtures.task(), request)

        assertEquals(request, transitionPort.lastRequest)
    }

    @Test
    fun `a successful transition is recorded as an operator action with its audit reference`() = runTest {
        useCase()(Fixtures.task(), Fixtures.transitionRequest())

        assertEquals(1, actionStore.recorded.size)
        val action = actionStore.recorded.single()
        assertEquals("SET_IN_PROGRESS", action.actionName)
        assertEquals("SUCCEEDED", action.outcomeLabel)
        assertEquals("audit-1", action.auditRef)
    }

    @Test
    fun `a successful transition records the resulting status in the workflow metadata`() = runTest {
        useCase()(Fixtures.task(), Fixtures.transitionRequest())

        val record = workflowStore.recorded.single()
        assertEquals(TaskStatus.IN_PROGRESS.wireValue, record.statusWire)
    }

    // -- fail closed, before anything is minted or sent --

    @Test
    fun `SET_DONE is refused before a capability is minted`() = runTest {
        val request = Fixtures.transitionRequest(
            kind = TransitionKind.SET_DONE,
            currentStatus = TaskStatus.IN_PROGRESS,
            targetStatus = TaskStatus.COMPLETED,
        )
        val result = useCase()(Fixtures.task(status = TaskStatus.IN_PROGRESS), request)

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals("POLICY_REFUSED", (result as RequestTransitionUseCase.TransitionResult.MintBlocked).code)
        assertEquals(0, mintingPort.transitionMints)
        assertEquals(0, transitionPort.dispatches)
    }

    @Test
    fun `a missing work-order id blocks the transition before minting`() = runTest {
        val result = useCase()(Fixtures.task(orderId = ""), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals(0, mintingPort.transitionMints)
        assertEquals(0, transitionPort.dispatches)
    }

    @Test
    fun `a halted plan blocks the transition before minting`() = runTest {
        val halted = FakeTaskGatewayPort(status = Fixtures.planStatus(halted = true))
        val result = useCase(gateway = halted)(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals("HALT_ACTIVE", (result as RequestTransitionUseCase.TransitionResult.MintBlocked).code)
        assertEquals(0, mintingPort.transitionMints)
        assertEquals(0, transitionPort.dispatches)
    }

    @Test
    fun `a drifted branch head blocks the transition before minting`() = runTest {
        val drifted = FakeBranchHeadPort(liveHeadSha = Fixtures.OTHER_HEAD_SHA)
        val result = useCase(branchHead = drifted)(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals("HEAD_DRIFT", (result as RequestTransitionUseCase.TransitionResult.MintBlocked).code)
        assertEquals(0, mintingPort.transitionMints)
        assertEquals(0, transitionPort.dispatches)
    }

    @Test
    fun `a missing operator credential blocks the transition before minting`() = runTest {
        val noCredential = FakeAuthorityPort(Fixtures.authority(hasOperatorCredential = false))
        val result = useCase(authority = noCredential)(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals("AUTH", (result as RequestTransitionUseCase.TransitionResult.MintBlocked).code)
        assertEquals(0, mintingPort.transitionMints)
        assertEquals(0, transitionPort.dispatches)
    }

    @Test
    fun `an environment mismatch blocks the transition before minting`() = runTest {
        val mismatched = FakeAuthorityPort(
            Fixtures.authority(
                expectedEnvironment = Environment.STAGING,
                reportedEnvironment = Environment.PRODUCTION,
            ),
        )
        val result = useCase(authority = mismatched)(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals("ENVIRONMENT_MISMATCH", (result as RequestTransitionUseCase.TransitionResult.MintBlocked).code)
        assertEquals(0, mintingPort.transitionMints)
        assertEquals(0, transitionPort.dispatches)
    }

    @Test
    fun `a declined confirmation sends nothing`() = runTest {
        val declined = FakeMintingPort(MintOutcome.Declined)
        val result = useCase(minting = declined)(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals("DECLINED", (result as RequestTransitionUseCase.TransitionResult.MintBlocked).code)
        assertEquals(0, transitionPort.dispatches)
    }

    @Test
    fun `an expired confirmation sends nothing`() = runTest {
        val expired = FakeMintingPort(MintOutcome.ExpiredWhileConfirming)
        val result = useCase(minting = expired)(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.MintBlocked)
        assertEquals("EXPIRED", (result as RequestTransitionUseCase.TransitionResult.MintBlocked).code)
        assertEquals(0, transitionPort.dispatches)
    }

    // -- gateway outcomes are reported faithfully --

    @Test
    fun `a gateway refusal is reported as a refusal, never as success`() = runTest {
        val refusing = RecordingStatusTransitionPort(
            OperationOutcome.Refused(code = "SEAL_NOT_ATTESTED", message = "no seal for this work order"),
        )
        val result = useCase(transitions = refusing)(Fixtures.task(), Fixtures.transitionRequest())

        assertTrue(result is RequestTransitionUseCase.TransitionResult.Executed)
        val outcome = (result as RequestTransitionUseCase.TransitionResult.Executed).outcome
        assertTrue(outcome is OperationOutcome.Refused)
        assertEquals("REFUSED:SEAL_NOT_ATTESTED", actionStore.recorded.single().outcomeLabel)
    }

    @Test
    fun `an uncertain completion keeps its idempotency key so recovery can look it up`() = runTest {
        val uncertain = RecordingStatusTransitionPort(
            OperationOutcome.UncertainCompletion(idempotencyKey = "a".repeat(32)),
        )
        val result = useCase(transitions = uncertain)(Fixtures.task(), Fixtures.transitionRequest())

        val outcome = (result as RequestTransitionUseCase.TransitionResult.Executed).outcome
        assertTrue(outcome is OperationOutcome.UncertainCompletion)
        assertEquals("a".repeat(32), actionStore.recorded.single().idempotencyKey)
    }

    @Test
    fun `a refusal never records a status change`() = runTest {
        val refusing = RecordingStatusTransitionPort(
            OperationOutcome.Refused(code = "POLICY_REFUSED", message = "nope"),
        )
        useCase(transitions = refusing)(Fixtures.task(), Fixtures.transitionRequest())

        val record = workflowStore.recorded.single()
        assertEquals(TaskStatus.READY.wireValue, record.statusWire)
    }

    @Test
    fun `only one transition is requested per operator action`() = runTest {
        useCase()(Fixtures.task(), Fixtures.transitionRequest())

        assertEquals(1, transitionPort.dispatches)
    }
}
