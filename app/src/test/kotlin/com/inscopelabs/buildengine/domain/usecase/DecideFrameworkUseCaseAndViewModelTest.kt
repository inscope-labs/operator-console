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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Stage 3.4 (T3-404 / PR3-025): DecideFrameworkUseCase + DecisionViewModel under ADR 0014 D2.
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
        notes = "Approved after review",
    )

    private fun confirmAndMint() = ConfirmAndMintCapabilityUseCase(
        capabilityMinter = minter,
        biometricGate = biometricGate,
        verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
        clock = clock,
    )

    @Test
    fun `missing plan task order branch or head returns InvalidTarget before prompt or send`() = runTest {
        val uc = DecideFrameworkUseCase(recordingRepo, confirmAndMint(), nonceSource)

        assertTrue(uc(sampleTarget().copy(plan = " ")) is DecideResult.InvalidTarget)
        assertTrue(uc(sampleTarget().copy(task = "")) is DecideResult.InvalidTarget)
        assertTrue(uc(sampleTarget().copy(order = "")) is DecideResult.InvalidTarget)
        assertTrue(uc(sampleTarget().copy(branch = "")) is DecideResult.InvalidTarget)
        assertTrue(uc(sampleTarget().copy(headSha = "")) is DecideResult.InvalidTarget)
        assertEquals(0, prompts)
        assertTrue(recordingRepo.decides.isEmpty())
    }

    @Test
    fun `forbidden patterns in notes return InvalidTarget`() = runTest {
        val uc = DecideFrameworkUseCase(recordingRepo, confirmAndMint(), nonceSource)
        val result = uc(sampleTarget().copy(notes = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"))
        assertTrue(result is DecideResult.InvalidTarget)
        assertEquals(0, prompts)
        assertTrue(recordingRepo.decides.isEmpty())
    }

    @Test
    fun `default settled path mints single DECIDE capability and journals via repository`() = runTest {
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
            // default is settled = true (ADR 0014 D2)
        )

        val result = uc(sampleTarget()) as DecideResult.Executed
        // Ack lacks headSha → UncertainCompletion is correct fail-closed behaviour (I-06)
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
    fun `explicit unsettled construction still blocks for regression`() = runTest {
        val uc = DecideFrameworkUseCase(
            operationRepository = recordingRepo,
            confirmAndMintCapability = confirmAndMint(),
            nonceSource = nonceSource,
            contractSchemaSettled = false,
        )
        val result = uc(sampleTarget())
        assertTrue(result is DecideResult.ContractBlocked)
        assertEquals(0, prompts)
        assertTrue(recordingRepo.decides.isEmpty())
    }

    @Test
    fun `DecisionViewModel under D2 has no contract blocker and submits schema fields only`() = runTest {
        val uc = DecideFrameworkUseCase(recordingRepo, confirmAndMint(), nonceSource)
        val vm = DecisionViewModel(SavedStateHandle(mapOf("decisionId" to "d1")), uc)

        assertNull(vm.uiState.value.contractBlockedReason)
        assertFalse(vm.uiState.value.canSubmit)

        vm.updatePlan("atlas")
        vm.updateTask("10008")
        vm.updateBranch(branch)
        vm.updateHeadSha(head)
        vm.updateOrder("order-7731")
        vm.updateNotes("Matches security boundary ADR")

        val state = vm.uiState.value
        assertTrue(state.hasRequiredFields)
        assertTrue(state.canSubmit)

        vm.submitDecision()
        val last = vm.uiState.value.lastResult
        assertTrue(last is DecideResult.Executed || last is DecideResult.NotAuthorized)
        // Repository was reached (or auth gate); never ContractBlocked under settled default
        assertFalse(last is DecideResult.ContractBlocked)
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
