package com.inscopelabs.buildengine.ui.publish

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
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
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCase
import com.inscopelabs.buildengine.domain.usecase.write.PublishOrderResolver
import com.inscopelabs.buildengine.domain.usecase.write.PublishTaskUseCase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

internal object PublishTestFixtures {
    const val TASK_ID = "10008"
    const val PLAN_ID = "atlas"
    const val BRANCH_NAME = "task-10008-refactor-parser"
    const val FULL_HEAD_SHA = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
    const val ORDER_ID = "order-7731"

    fun createPublishViewModel(
        context: Context,
        taskId: String = TASK_ID,
        order: String? = ORDER_ID,
        tier: AuthTier? = AuthTier.HARDWARE_CONFIRM,
        environment: String = "staging",
        taskRepository: FakePublishTaskRepository = FakePublishTaskRepository(),
        operationRepository: FakePublishOperationRepository = FakePublishOperationRepository(),
        biometricAnswer: Boolean = true,
    ): PublishViewModel {
        val clock = FixedClock(1700000000L)
        val nonceSource = DeviceNonceSource(SecureRandomSource())
        val minter = CapabilityMinter(
            signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
            nonceSource = nonceSource,
            clock = clock,
        )
        val enrollmentStore = DeviceEnrollmentStore(preferences = InMemorySharedPreferences()).apply {
            if (tier != null) {
                recordPending(
                    kid = "k1",
                    keyAlias = "op_${environment}_host",
                    publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE",
                    now = 1700000000L,
                    authTier = tier.wireValue,
                    environment = environment,
                )
                activate("k1", 1700000000L, environment = environment)
            }
        }
        val dsFile = File(context.cacheDir, "publish_test_${UUID.randomUUID()}.preferences_pb")
        val healthStore = HealthSnapshotStore(PreferenceDataStoreFactory.create { dsFile })
        runBlocking {
            healthStore.save(
                GatewayHealthSnapshot(
                    status = "ok",
                    environment = environment,
                    version = "1.0.0",
                    timestampMillis = 1700000000000L,
                    probedAtEpochSeconds = 1700000000L,
                ),
            )
        }
        val confirmAndMint = ConfirmAndMintCapabilityUseCase(
            capabilityMinter = minter,
            biometricGate = BiometricGate { _, _ -> biometricAnswer },
            verifyBranchHeadUseCase = VerifyBranchHeadUseCase(taskRepository),
            enrollmentStore = enrollmentStore,
            clock = clock,
            healthSnapshotStore = healthStore,
            expectedEnvironmentProvider = { environment },
        )
        val useCase = PublishTaskUseCase(
            operationRepository = operationRepository,
            confirmAndMintCapability = confirmAndMint,
            nonceSource = nonceSource,
        )
        return PublishViewModel(
            savedStateHandle = SavedStateHandle(mapOf("taskId" to taskId)),
            publishTask = useCase,
            taskRepository = taskRepository,
            orderResolver = PublishOrderResolver { order },
            enrollmentStore = enrollmentStore,
            healthSnapshotStore = healthStore,
            capabilityMinter = minter,
        )
    }

    class FakePublishTaskRepository : TaskRepository {
        var taskResult: (String) -> AppResult<Task> = { id ->
            AppResult.Success(
                Task(
                    id = id,
                    planId = PLAN_ID,
                    title = "Refactor parser",
                    status = TaskStatus.IN_PROGRESS,
                    branch = BRANCH_NAME,
                    headSha = FULL_HEAD_SHA,
                ),
            )
        }
        var branchResult: (String) -> AppResult<TaskBranch> = {
            AppResult.Success(TaskBranch(name = BRANCH_NAME, headSha = FULL_HEAD_SHA))
        }

        override suspend fun getNextTasks(): AppResult<List<Task>> = AppResult.Success(emptyList())
        override suspend fun getTask(taskId: String): AppResult<Task> = taskResult(taskId)
        override suspend fun branchFor(taskId: String): AppResult<TaskBranch> = branchResult(taskId)
        override suspend fun liveHead(branch: String): AppResult<String> = AppResult.Success(FULL_HEAD_SHA)
        override suspend fun setStatus(taskId: String, status: TaskStatus): AppResult<Task> =
            AppResult.Failure("unsupported", "unused")
    }

    class FakePublishOperationRepository : OperationRepository {
        var publishCalls = 0
        var onPublish: suspend (OperationRequest, SignedCapability) -> OperationOutcome = { _, _ ->
            OperationOutcome.Succeeded(auditRef = "audit-20261113-1", headSha = FULL_HEAD_SHA)
        }

        override suspend fun publish(request: OperationRequest, capability: SignedCapability): OperationOutcome {
            publishCalls++
            return onPublish(request, capability)
        }

        override suspend fun upload(
            request: OperationRequest,
            submissionId: String,
            zipSha256: String,
            zipBase64: String,
            capability: SignedCapability,
        ): OperationOutcome = error("unused")

        override suspend fun reconcile(
            request: OperationRequest,
            submissionId: String,
            capability: SignedCapability,
        ): OperationOutcome = error("unused")

        override suspend fun halt(
            request: OperationRequest,
            reason: String,
            capability: SignedCapability,
        ): OperationOutcome = error("unused")

        override suspend fun resume(
            request: OperationRequest,
            reason: String,
            capability: SignedCapability,
        ): OperationOutcome = error("unused")

        override suspend fun recordedHaltCause(): String? = null

        override suspend fun setTaskStatus(
            request: OperationRequest,
            targetStatus: String,
            capability: SignedCapability,
        ): OperationOutcome = error("unused")

        override suspend fun decide(
            request: OperationRequest,
            decision: String,
            notes: String?,
            capability: SignedCapability,
        ): OperationOutcome = error("unused")
    }

    class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            data[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class Editor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { key?.let { temp[it] = values } }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun remove(key: String?): SharedPreferences.Editor = apply { key?.let { temp[it] = null } }
            override fun clear(): SharedPreferences.Editor = apply { clear = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clear) data.clear()
                temp.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            }
        }
    }
}
