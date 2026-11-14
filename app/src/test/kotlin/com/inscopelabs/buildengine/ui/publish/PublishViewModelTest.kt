package com.inscopelabs.buildengine.ui.publish

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.BiometricGate
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.HealthSnapshotStore
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.PublishOrderResolver
import com.inscopelabs.buildengine.domain.usecase.write.PublishTaskUseCase
import com.inscopelabs.buildengine.domain.usecase.write.UnresolvedPublishOrderResolver
import com.inscopelabs.buildengine.testutil.FakeSharedPreferences
import com.inscopelabs.buildengine.testutil.FakeTaskRepository
import com.inscopelabs.buildengine.testutil.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Stage 3.3 (T3-301, T3-303): ViewModel tests for [PublishViewModel] with fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PublishViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var enrollmentStore: DeviceEnrollmentStore
    private lateinit var healthSnapshotStore: HealthSnapshotStore
    private lateinit var operationRepository: FakePublishOperationRepository
    private lateinit var clock: FixedClock
    private lateinit var nonceSource: DeviceNonceSource
    private lateinit var minter: CapabilityMinter

    private var biometricPrompts = 0
    private var biometricDecision = true

    private val taskId = "10008"
    private val planId = "atlas"
    private val branchName = "task-10008-refactor-parser"
    private val staleTaskHead = "0".repeat(40)
    private val liveHead = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"

    @Before
    fun setUp() {
        clock = FixedClock(1700000000L)
        nonceSource = DeviceNonceSource(SecureRandomSource())
        minter = CapabilityMinter(
            signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
            nonceSource = nonceSource,
            clock = clock,
        )
        taskRepository = FakeTaskRepository().apply {
            singleTaskResult = { id ->
                AppResult.Success(
                    Task(
                        id = id,
                        planId = planId,
                        title = "Refactor parser",
                        status = TaskStatus.IN_PROGRESS,
                        branch = branchName,
                        headSha = staleTaskHead,
                    ),
                )
            }
            branchResult = {
                AppResult.Success(TaskBranch(name = branchName, headSha = liveHead))
            }
        }
        enrollmentStore = DeviceEnrollmentStore(preferences = FakeSharedPreferences())
        val dsFile = File(temporaryFolder.root, "publish_vm_health.preferences_pb")
        healthSnapshotStore = HealthSnapshotStore(
            PreferenceDataStoreFactory.create(produceFile = { dsFile }),
        )
        runBlocking {
            healthSnapshotStore.save(
                GatewayHealthSnapshot(
                    status = "ok",
                    environment = "staging",
                    version = "1.0.0",
                    timestampMillis = 1700000000000L,
                    probedAtEpochSeconds = 1700000000L,
                ),
            )
        }
        operationRepository = FakePublishOperationRepository()
        biometricPrompts = 0
        biometricDecision = true
    }

    private fun enrollActive(tier: AuthTier, environment: String? = "staging") {
        enrollmentStore.recordPending(
            kid = "k1",
            keyAlias = "op_staging_example",
            publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE",
            now = 1700000000L,
            authTier = tier.wireValue,
            environment = environment,
        )
        enrollmentStore.activate("k1", 1700000000L, environment = environment)
    }

    private fun createViewModel(
        taskIdArg: String? = taskId,
        orderResolver: PublishOrderResolver = PublishOrderResolver { "order-7731" },
        expectedEnv: String? = "staging",
    ): PublishViewModel {
        val savedState = if (taskIdArg != null) {
            SavedStateHandle(mapOf("taskId" to taskIdArg))
        } else {
            SavedStateHandle()
        }
        val confirmAndMint = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = BiometricGate { _, _ ->
                biometricPrompts++
                biometricDecision
            },
            verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
            enrollmentStore = enrollmentStore,
            clock = clock,
            healthSnapshotStore = healthSnapshotStore,
            expectedEnvironmentProvider = expectedEnv?.let { { it } },
        )
        val useCase = PublishTaskUseCase(
            operationRepository = operationRepository,
            confirmAndMintCapability = confirmAndMint,
            nonceSource = nonceSource,
        )
        return PublishViewModel(
            savedStateHandle = savedState,
            publishTask = useCase,
            taskRepository = taskRepository,
            orderResolver = orderResolver,
            enrollmentStore = enrollmentStore,
            healthSnapshotStore = healthSnapshotStore,
            capabilityMinter = minter,
        )
    }

    private suspend fun awaitLoadedState(vm: PublishViewModel): PublishUiState {
        var latest = vm.uiState.value
        vm.uiState.test {
            latest = awaitItem()
            while (latest is PublishUiState.Idle || latest is PublishUiState.Loading) {
                latest = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
        return latest
    }

    @Test
    fun `load transitions to Ready with live branch head, order, tier, environment, and ttl`() = runTest {
        enrollActive(AuthTier.HARDWARE_CONFIRM)
        val vm = createViewModel()

        val state = awaitLoadedState(vm)

        assertTrue("Expected Ready, got $state", state is PublishUiState.Ready)
        val summary = (state as PublishUiState.Ready).summary
        assertEquals(taskId, summary.taskId)
        assertEquals("Refactor parser", summary.taskTitle)
        assertEquals(planId, summary.plan)
        assertEquals("order-7731", summary.order)
        assertEquals(branchName, summary.branch)
        assertEquals(liveHead, summary.headSha)
        assertEquals(AuthTier.HARDWARE_CONFIRM, summary.tier)
        assertEquals("staging", summary.environment)
        assertEquals(minter.ttlSeconds, summary.ttlSeconds)
        assertTrue(summary.canConfirm)
    }

    @Test
    fun `missing or blank taskId transitions to LoadFailed`() = runTest {
        val vm = createViewModel(taskIdArg = null)

        val state = awaitLoadedState(vm)

        assertEquals(PublishUiState.LoadFailed("No task selected"), state)
    }

    @Test
    fun `load transitions to LoadFailed when getTask fails`() = runTest {
        taskRepository.singleTaskResult = { AppResult.Failure("not_found", "Task missing") }
        val vm = createViewModel()

        val state = awaitLoadedState(vm)

        assertEquals(PublishUiState.LoadFailed("Task missing"), state)
    }

    @Test
    fun `load transitions to LoadFailed when branchFor fails`() = runTest {
        taskRepository.branchResult = { AppResult.Failure("unreachable", "Gateway unreachable") }
        val vm = createViewModel()

        val state = awaitLoadedState(vm)

        assertEquals(PublishUiState.LoadFailed("Gateway unreachable"), state)
    }

    @Test
    fun `confirm is ignored when order is unresolved`() = runTest {
        enrollActive(AuthTier.HARDWARE_CONFIRM)
        val vm = createViewModel(orderResolver = UnresolvedPublishOrderResolver())

        val ready = awaitLoadedState(vm) as PublishUiState.Ready
        assertNull(ready.summary.order)
        assertFalse(ready.summary.canConfirm)

        vm.confirm()

        assertTrue(vm.uiState.value is PublishUiState.Ready)
        assertEquals(0, biometricPrompts)
        assertEquals(0, operationRepository.publishCalls)
    }

    @Test
    fun `confirm is ignored when device is not enrolled so tier is unknown`() = runTest {
        val vm = createViewModel()

        val ready = awaitLoadedState(vm) as PublishUiState.Ready
        assertEquals("order-7731", ready.summary.order)
        assertNull(ready.summary.tier)
        assertFalse(ready.summary.canConfirm)

        vm.confirm()

        assertTrue(vm.uiState.value is PublishUiState.Ready)
        assertEquals(0, biometricPrompts)
        assertEquals(0, operationRepository.publishCalls)
    }

    @Test
    fun `confirm is ignored when state is LoadFailed`() = runTest {
        taskRepository.singleTaskResult = { AppResult.Failure("offline", "Offline") }
        val vm = createViewModel()

        val failed = awaitLoadedState(vm)
        assertTrue(failed is PublishUiState.LoadFailed)

        vm.confirm()

        assertEquals(failed, vm.uiState.value)
        assertEquals(0, operationRepository.publishCalls)
    }

    @Test
    fun `one submission per confirmation even when confirm is called repeatedly`() = runTest {
        enrollActive(AuthTier.HARDWARE_CONFIRM)
        val gate = CompletableDeferred<OperationOutcome>()
        operationRepository.onPublish = { _, _ -> gate.await() }

        val vm = createViewModel()
        awaitLoadedState(vm)

        vm.uiState.test {
            assertTrue(awaitItem() is PublishUiState.Ready)

            vm.confirm()
            val submitting = awaitItem()
            assertTrue(submitting is PublishUiState.Submitting)

            // Second tap while Submitting must be ignored.
            vm.confirm()

            gate.complete(OperationOutcome.Succeeded(auditRef = "audit-single-1", headSha = liveHead))
            val finished = awaitItem()
            assertTrue(finished is PublishUiState.Finished)
            assertEquals(1, operationRepository.publishCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Finished carries Succeeded banner and auditRef on verified success`() = runTest {
        enrollActive(AuthTier.HARDWARE_CONFIRM)
        operationRepository.onPublish = { _, _ ->
            OperationOutcome.Succeeded(auditRef = "audit-20261113-99", headSha = liveHead)
        }
        val vm = createViewModel()
        awaitLoadedState(vm)

        vm.uiState.test {
            assertTrue(awaitItem() is PublishUiState.Ready)

            vm.confirm()

            var latest = awaitItem()
            if (latest is PublishUiState.Submitting) {
                latest = awaitItem()
            }
            assertTrue("Expected Finished, got $latest", latest is PublishUiState.Finished)
            val finished = latest as PublishUiState.Finished
            assertEquals(PublishBannerKind.SUCCEEDED, finished.banner.kind)
            assertTrue(finished.banner.isSuccess)
            assertEquals("audit-20261113-99", finished.banner.auditRef)
            assertEquals(liveHead, finished.banner.detail)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `in-app confirmation forwards typed head prefix and Finished carries refusal auditRef`() = runTest {
        enrollActive(AuthTier.IN_APP_CONFIRM)
        operationRepository.onPublish = { _, _ ->
            OperationOutcome.Refused(code = "HALT_ACTIVE", auditRef = "audit-halt-7")
        }
        val vm = createViewModel()
        awaitLoadedState(vm)

        vm.onTypedHeadChanged(liveHead.take(PublishSummary.TYPED_HEAD_LENGTH))
        vm.uiState.test {
            val ready = awaitItem() as PublishUiState.Ready
            assertEquals(liveHead.take(7), ready.typedHead)

            vm.confirm()

            var latest = awaitItem()
            if (latest is PublishUiState.Submitting) {
                latest = awaitItem()
            }
            val finished = latest as PublishUiState.Finished
            assertEquals(PublishBannerKind.HALT_ACTIVE, finished.banner.kind)
            assertFalse(finished.banner.isSuccess)
            assertEquals("HALT_ACTIVE", finished.banner.code)
            assertEquals("audit-halt-7", finished.banner.auditRef)
            assertEquals(0, biometricPrompts)
            assertEquals(1, operationRepository.publishCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakePublishOperationRepository : OperationRepository {
        var publishCalls = 0
        var onPublish: suspend (OperationRequest, SignedCapability) -> OperationOutcome = { _, _ ->
            OperationOutcome.Succeeded(auditRef = "audit-default", headSha = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d")
        }

        override suspend fun publish(request: OperationRequest, capability: SignedCapability): OperationOutcome {
            publishCalls++
            return onPublish(request, capability)
        }

        override suspend fun upload(request: OperationRequest): OperationResult = error("unused")
        override suspend fun reconcile(request: OperationRequest): OperationResult = error("unused")
        override suspend fun halt(request: OperationRequest): OperationResult = error("unused")
        override suspend fun resume(request: OperationRequest): OperationResult = error("unused")
        override suspend fun decide(request: OperationRequest): OperationResult = error("unused")
    }
}
