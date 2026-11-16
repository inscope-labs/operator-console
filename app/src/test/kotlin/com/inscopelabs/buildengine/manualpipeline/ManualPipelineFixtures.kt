package com.inscopelabs.buildengine.manualpipeline

import com.inscopelabs.buildengine.manualpipeline.domain.model.ArchiveDigest
import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import com.inscopelabs.buildengine.manualpipeline.domain.model.ImplementationStage
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperatorActionRecord
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthorityPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthoritySnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadInfo
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.Clock
import com.inscopelabs.buildengine.manualpipeline.domain.port.ExecutorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.HandoffEvidenceStore
import com.inscopelabs.buildengine.manualpipeline.domain.port.JournalPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintedCapabilityHandle
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintingPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperationLookupPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperatorActionStore
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanStatusSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanSummary
import com.inscopelabs.buildengine.manualpipeline.domain.port.RedactorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.StatusTransitionPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.UploadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared JVM fixtures and fakes for the manual pipeline module's domain tests.
 *
 * Everything here is deterministic: no wall clock, no network, no randomness, and no
 * Android framework dependency, so the module's decision logic can be exercised on the
 * JVM (the CI job runs `:app:testDebugUnitTest`).
 */
object Fixtures {

    const val PLAN_ID = "plan-alpha"
    const val TASK_ID = "task-42"
    const val ORDER_ID = "order-7"
    const val BRANCH = "feature/manual-pipeline"
    const val SUBMISSION_ID = "sub-9"

    /** A full 40-character lowercase hex commit SHA. */
    const val HEAD_SHA = "0123456789abcdef0123456789abcdef01234567"

    /** A second full 40-character lowercase hex commit SHA, for drift scenarios. */
    const val OTHER_HEAD_SHA = "89abcdef0123456789abcdef0123456789abcdef"

    /** A valid 64-character hex SHA-256 archive digest. */
    const val ZIP_SHA256 = "b3b1a0c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f80"

    fun task(
        id: String = TASK_ID,
        orderId: String = ORDER_ID,
        status: TaskStatus = TaskStatus.READY,
        headSha: String = HEAD_SHA,
        isPlanHalted: Boolean = false,
        isBlockedByGateway: Boolean = false,
        branch: String = BRANCH,
    ): ManualTask = ManualTask(
        id = id,
        planId = PLAN_ID,
        title = "Manual pipeline task",
        orderId = orderId,
        stage = ImplementationStage(id = "stage-1", title = "Stage 1", orderIndex = 0),
        status = status,
        branch = branch,
        headSha = headSha,
        isPlanHalted = isPlanHalted,
        isBlockedByGateway = isBlockedByGateway,
    )

    fun authority(
        hasOperatorCredential: Boolean = true,
        enrolled: Boolean = true,
        confirmationTier: ConfirmationTier? = ConfirmationTier.HARDWARE_CONFIRM,
        expectedEnvironment: Environment = Environment.STAGING,
        reportedEnvironment: Environment = Environment.STAGING,
        tierAllowed: Boolean = true,
        tierReason: String = "",
    ): AuthoritySnapshot = AuthoritySnapshot(
        hasOperatorCredential = hasOperatorCredential,
        enrolled = enrolled,
        activeKid = "kid-1",
        confirmationTier = confirmationTier,
        hardwareConfirmAvailable = true,
        expectedEnvironment = expectedEnvironment,
        reportedEnvironment = reportedEnvironment,
        tierReason = tierReason,
        tierAllowed = tierAllowed,
    )

    fun planStatus(
        planId: String = PLAN_ID,
        halted: Boolean = false,
        blockedTasks: List<String> = emptyList(),
    ): PlanStatusSnapshot = PlanStatusSnapshot(
        planId = planId,
        status = if (halted) "halted" else "active",
        halted = halted,
        openBranches = listOf(BRANCH),
        blockedTasks = blockedTasks,
        currentMilestone = "stage-1",
    )

    fun mintedHandle(
        handleId: String = "handle-1",
        branch: String = BRANCH,
        headSha: String = HEAD_SHA,
        tier: ConfirmationTier = ConfirmationTier.HARDWARE_CONFIRM,
    ): MintedCapabilityHandle = MintedCapabilityHandle(
        handleId = handleId,
        operation = "SET_IN_PROGRESS",
        planId = PLAN_ID,
        taskId = TASK_ID,
        orderId = ORDER_ID,
        branch = branch,
        headSha = headSha,
        ttlSeconds = 300L,
        tier = tier,
    )

    fun digest(
        zipSha256: String = ZIP_SHA256,
        totalSizeBytes: Long = 2048L,
    ): ArchiveDigest = ArchiveDigest(
        zipSha256 = zipSha256,
        totalSizeBytes = totalSizeBytes,
        fileDigests = mapOf("submission/report.md" to ZIP_SHA256),
        fileSizesBytes = mapOf("submission/report.md" to totalSizeBytes),
    )

    fun manifest(
        submissionId: String = SUBMISSION_ID,
        declaredPaths: List<String> = listOf("submission/report.md"),
        suppliedPaths: List<String> = listOf("submission/report.md"),
        missingDeclaredPaths: List<String> = emptyList(),
        extraSuppliedPaths: List<String> = emptyList(),
        digest: ArchiveDigest = Fixtures.digest(),
    ): SubmissionManifest = SubmissionManifest(
        submissionId = submissionId,
        taskId = TASK_ID,
        declaredPaths = declaredPaths,
        suppliedPaths = suppliedPaths,
        missingDeclaredPaths = missingDeclaredPaths,
        extraSuppliedPaths = extraSuppliedPaths,
        digest = digest,
        sourceDisplayName = "submission.zip",
    )

    fun transitionRequest(
        kind: TransitionKind = TransitionKind.SET_IN_PROGRESS,
        currentStatus: TaskStatus = TaskStatus.READY,
        targetStatus: TaskStatus = TaskStatus.IN_PROGRESS,
        expectedHead: String = HEAD_SHA,
        branch: String = BRANCH,
    ): TransitionRequest = TransitionRequest(
        planId = PLAN_ID,
        taskId = TASK_ID,
        orderId = ORDER_ID,
        branch = branch,
        expectedHead = expectedHead,
        kind = kind,
        currentStatus = currentStatus,
        targetStatus = targetStatus,
    )
}

/** Fixed clock for the module's [Clock] port. */
class FixedTestClock(private var millis: Long = 1_761_840_000_000L) : Clock {
    override fun nowEpochMillis(): Long = millis

    fun advance(millis: Long) {
        this.millis += millis
    }
}

/**
 * Redactor that reports the forbidden material the module must never persist.
 * The real implementation lives in `util/Redactor`; this fake only needs to be strict.
 */
class StrictTestRedactor : RedactorPort {
    override fun redact(input: String): String =
        input.replace(Regex("(?i)(nonce|signature|capability|privateKey|bearer|credential)"), "[REDACTED]")

    override fun containsForbidden(input: String): Boolean =
        Regex("(?i)(nonce|signature|capability|privateKey|bearer|credential|toWire)").containsMatchIn(input)
}

class FakeAuthorityPort(private val snapshot: AuthoritySnapshot) : AuthorityPort {
    var requestedOperations: MutableList<String> = mutableListOf()

    override suspend fun currentAuthority(operationName: String): AuthoritySnapshot {
        requestedOperations += operationName
        return snapshot
    }

    override suspend fun isEnvironmentMatched(): Boolean = snapshot.environmentMatched
}

class FakeTaskGatewayPort(
    private val status: PlanStatusSnapshot = Fixtures.planStatus(),
    private val tasks: List<ManualTask> = emptyList(),
) : TaskGatewayPort {
    var planStatusCalls: Int = 0
        private set

    override suspend fun fetchPlans(): Result<List<PlanSummary>> = Result.success(emptyList())

    override suspend fun fetchPlanStatus(planId: String): Result<PlanStatusSnapshot> {
        planStatusCalls++
        return Result.success(status)
    }

    override suspend fun fetchNextTasks(): Result<List<ManualTask>> = Result.success(tasks)

    override suspend fun fetchStages(planId: String): Result<List<ImplementationStage>> =
        Result.success(emptyList())
}

class FakeBranchHeadPort(private val liveHeadSha: String = Fixtures.HEAD_SHA) : BranchHeadPort {
    var requestedBranches: MutableList<String> = mutableListOf()

    override suspend fun fetchLiveHead(branch: String): Result<BranchHeadInfo> {
        requestedBranches += branch
        return Result.success(BranchHeadInfo(branch = branch, headSha = liveHeadSha))
    }
}

/** Minting port that records every mint attempt and replays a scripted outcome. */
class FakeMintingPort(private val outcome: MintOutcome) : MintingPort {
    var transitionMints: Int = 0
        private set
    var uploadMints: Int = 0
        private set

    override suspend fun mintForTransition(request: TransitionRequest): MintOutcome {
        transitionMints++
        return outcome
    }

    override suspend fun mintForUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        typedHeadConfirmation: String?,
    ): MintOutcome {
        uploadMints++
        return outcome
    }
}

/** Transition port that records dispatches so tests can assert nothing was sent. */
class RecordingStatusTransitionPort(
    private val outcome: OperationOutcome = OperationOutcome.Succeeded(
        auditRef = "audit-1",
        headSha = Fixtures.HEAD_SHA,
    ),
) : StatusTransitionPort {
    var dispatches: Int = 0
        private set
    var lastRequest: TransitionRequest? = null
        private set

    override suspend fun submitTransition(
        request: TransitionRequest,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome {
        dispatches++
        lastRequest = request
        return outcome
    }
}

/** Upload port that records dispatches so tests can assert nothing was sent. */
class RecordingUploadPort(
    private val outcome: OperationOutcome = OperationOutcome.Succeeded(
        auditRef = "audit-2",
        headSha = Fixtures.HEAD_SHA,
    ),
) : UploadPort {
    var dispatches: Int = 0
        private set
    var lastZipBase64: String? = null
        private set
    var lastZipSha256: String? = null
        private set

    override suspend fun submitUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome {
        dispatches++
        lastZipBase64 = zipBase64
        lastZipSha256 = zipSha256
        return outcome
    }
}

/** In-memory operator action log. */
class RecordingOperatorActionStore : OperatorActionStore {
    val recorded: MutableList<OperatorActionRecord> = mutableListOf()

    override fun observeActionsForTask(taskId: String): Flow<List<OperatorActionRecord>> =
        flowOf(recorded.filter { it.taskId == taskId })

    override fun observeAllActions(): Flow<List<OperatorActionRecord>> = flowOf(recorded.toList())

    override suspend fun recordAction(record: OperatorActionRecord) {
        recorded += record
    }
}

/** In-memory workflow metadata store. */
class RecordingWorkflowRecordStore : WorkflowRecordStore {
    val recorded: MutableList<WorkflowRecordSnapshot> = mutableListOf()

    override fun observeRecordsForTask(taskId: String): Flow<List<WorkflowRecordSnapshot>> =
        flowOf(recorded.filter { it.taskId == taskId })

    override suspend fun latestForTask(taskId: String): WorkflowRecordSnapshot? =
        recorded.lastOrNull { it.taskId == taskId }

    override suspend fun saveRecord(record: WorkflowRecordSnapshot) {
        recorded += record
    }
}

/** In-memory handoff evidence store. */
class RecordingHandoffEvidenceStore : HandoffEvidenceStore {
    val saved: MutableList<HandoffEvidence> = mutableListOf()

    override fun observeForTask(taskId: String): Flow<List<HandoffEvidence>> =
        flowOf(saved.filter { it.taskId == taskId })

    override suspend fun latestForTask(taskId: String): HandoffEvidence? =
        saved.lastOrNull { it.taskId == taskId }

    override suspend fun save(evidence: HandoffEvidence) {
        saved += evidence
    }
}

/** Journal port over a fixed set of records. */
class FakeJournalPort(private val records: List<OperationRecord> = emptyList()) : JournalPort {
    override suspend fun findAllRecords(): List<OperationRecord> = records

    override suspend fun findUncertainOrInFlight(): List<OperationRecord> =
        records.filter { it.state.equals("UNCERTAIN", true) || it.state.equals("IN_FLIGHT", true) }

    override suspend fun findByKey(idempotencyKey: String): OperationRecord? =
        records.firstOrNull { it.idempotencyKey == idempotencyKey }

    override suspend fun hasPendingIntents(): Boolean = findUncertainOrInFlight().isNotEmpty()
}

/** Read-only operation lookup port. */
class FakeOperationLookupPort(
    private val result: Result<RecoveryStatus> = Result.failure(IllegalStateException("not stubbed")),
) : OperationLookupPort {
    var calls: Int = 0
        private set

    override suspend fun lookupOperation(idempotencyKey: String): Result<RecoveryStatus> {
        calls++
        return result
    }
}

/** Executor port that only performs lookups; it never writes. */
class LookupOnlyExecutorPort(
    private val lookupOutcome: OperationOutcome = OperationOutcome.RecoveryRequired(
        idempotencyKey = "key",
        code = "OPERATION_NOT_FOUND",
    ),
) : ExecutorPort {
    var lookupCalls: Int = 0
        private set
    var transitionWrites: Int = 0
        private set
    var uploadWrites: Int = 0
        private set

    override suspend fun executeTransition(
        request: TransitionRequest,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome {
        transitionWrites++
        return lookupOutcome
    }

    override suspend fun executeUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome {
        uploadWrites++
        return lookupOutcome
    }

    override suspend fun lookupOperation(idempotencyKey: String): OperationOutcome {
        lookupCalls++
        return lookupOutcome
    }

    override suspend fun recoverUncertainOperations(): Map<String, OperationOutcome> = emptyMap()
}
