package com.inscopelabs.buildengine.domain.usecase

import androidx.lifecycle.SavedStateHandle
import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.dto.DecideRequestDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator
import com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.DecideFrameworkUseCase
import com.inscopelabs.buildengine.domain.usecase.write.DecideResult
import com.inscopelabs.buildengine.domain.usecase.write.DecideTarget
import com.inscopelabs.buildengine.domain.usecase.write.FrameworkDecision
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.InMemoryOperationJournalDao
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import com.inscopelabs.buildengine.testutil.RecordingFakeJournalCipher
import com.inscopelabs.buildengine.testutil.RecordingStage34OperationRepository
import com.inscopelabs.buildengine.ui.decision.DecisionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Stage 3.4 (T3-404 / PR3-003 / PR3-025): DecideFrameworkUseCase + DecisionViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DecideFrameworkUseCaseAndViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FixedClock(1700000000L)
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
        nonceSource = nonceSource,
        clock = clock,
    )
    private val branch = "task-10008-refactor-parser"
    private val head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
    private val taskRepository = FakeTaskRepository().apply {
        liveHeadResult = { AppResult.Success(head) }
        branchResult = { AppResult.Success(TaskBranch(name = branch, headSha = head)) }
    }
    private var prompts = 0
    private val biometricGate = BiometricGate { _, _ ->
        prompts++
        true
    }
    private val recordingRepo = RecordingStage34OperationRepository()

    private fun sampleTarget() = DecideTarget(
        plan = "atlas",
        task = "10008",
        branch = branch,
        headSha = head,
        order = "order-7731",
        decision = FrameworkDecision.APPROVED,
        attribution = "Lead Architect",
        rationale = "Matches security boundary ADR",
        scope = "Module: parser",
        evidence = "ADR-0005 review log #14",
        notes = "Approved after review",
    )

    private fun confirmAndMint() = ConfirmAndMintCapabilityUseCase(
        capabilityMinter = minter,
        biometricGate = biometricGate,
        verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
        clock = clock,
    )

    @Test
    fun `missing required attribution, rationale, scope, or evidence returns InvalidTarget before prompt or send`() = runTest {
        val uc = DecideFrameworkUseCase(recordingRepo, confirmAndMint(), nonceSource)

        assertTrue(uc(sampleTarget().copy(attribution = " ")) is DecideResult.InvalidTarget)
        assertTrue(uc(sampleTarget().copy(rationale = "")) is DecideResult.InvalidTarget)
        assertTrue(uc(sampleTarget().copy(scope = "")) is DecideResult.InvalidTarget)
        assertTrue(uc(sampleTarget().copy(evidence = " ")) is DecideResult.InvalidTarget)
        assertEquals(0, prompts)
        assertTrue(recordingRepo.decides.isEmpty())
    }

    @Test
    fun `default configuration fails closed with ContractBlocked under PR3-003 without minting or sending`() = runTest {
        val uc = DecideFrameworkUseCase(recordingRepo, confirmAndMint(), nonceSource)

        val result = uc(sampleTarget())

        assertTrue(result is DecideResult.ContractBlocked)
        assertTrue((result as DecideResult.ContractBlocked).reason.contains("PR3-003"))
        assertEquals(0, prompts)
        assertTrue(recordingRepo.decides.isEmpty())
    }

    @Test
    fun `DecisionViewModel exposes PR3-003 blocker, disables submission, and never sends when blocked`() = runTest {
        val uc = DecideFrameworkUseCase(recordingRepo, confirmAndMint(), nonceSource)
        val vm = DecisionViewModel(SavedStateHandle(mapOf("decisionId" to "D-01")), uc)

        vm.updatePlan("atlas")
        vm.updateTask("10008")
        vm.updateOrder("order-7731")
        vm.updateBranch(branch)
        vm.updateHeadSha(head)
        vm.selectDecision(FrameworkDecision.REJECTED)
        vm.updateAttribution("Lead Architect")
        vm.updateRationale("Does not satisfy T3-302")
        vm.updateScope("gateway/decisions")
        vm.updateEvidence("Schema diff audit")

        val state = vm.uiState.value
        assertTrue(state.hasRequiredFields)
        assertFalse(state.canSubmit)
        assertNotNull(state.contractBlockedReason)

        vm.submitDecision()
        assertTrue(vm.uiState.value.lastResult is DecideResult.ContractBlocked)
        assertEquals(0, prompts)
        assertTrue(recordingRepo.decides.isEmpty())
    }

    @Test
    fun `when schema is settled, decide mints single DECIDE capability and executor keeps missing headSha as UncertainCompletion`() = runTest {
        val dao = InMemoryOperationJournalDao()
        val cipher = RecordingFakeJournalCipher()
        val gateway = FakeGatewayApi().apply { branchHeadResult = { head } }
        val realRepo = OperationRepositoryImpl(
            gateway = gateway,
            journalDao = dao,
            cipher = cipher,
            keyGenerator = IdempotencyKeyGenerator(SecureRandomSource()),
            clock = clock,
        )
        val uc = DecideFrameworkUseCase(
            operationRepository = realRepo,
            confirmAndMintCapability = confirmAndMint(),
            nonceSource = nonceSource,
            contractSchemaSettled = true,
        )

        val result = uc(sampleTarget()) as DecideResult.Executed
        assertTrue(
            "Expected UncertainCompletion when decide ack lacks headSha, got ${result.outcome}",
            result.outcome is OperationOutcome.UncertainCompletion,
        )
        assertEquals(1, prompts)
        assertEquals(1, gateway.decideCalls.size)
        val sent = gateway.decideCalls.single()
        assertEquals("APPROVED", sent.decision)
        assertEquals("Approved after review", sent.notes)
        assertTrue(sent.capability.contains("\n${OperationType.DECIDE.name}\n"))
    }

    @Test
    fun `DecideRequestDto serializes strictly to decide schema properties`() {
        val dto = DecideRequestDto(
            plan = "atlas",
            task = "10008",
            branch = branch,
            decision = "APPROVED",
            notes = "ok",
            idempotencyKey = "a".repeat(32),
            capability = "cap",
        )
        val keys = Json.parseToJsonElement(Json.encodeToString(dto)).jsonObject.keys
        assertEquals(
            setOf("plan", "task", "branch", "decision", "notes", "idempotencyKey", "capability"),
            keys,
        )
    }
}
