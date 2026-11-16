package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.Clock
import com.inscopelabs.buildengine.manualpipeline.domain.port.HandoffEvidenceStore
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import java.util.UUID
import javax.inject.Inject

/**
 * MP-014: Assembles and persists the `PRE_TRANSFER_HANDOFF_READY` evidence bundle once both
 * archive upload and target status transition are verified against live Gateway A read-back.
 */
class VerifyHandoffUseCase @Inject constructor(
    private val branchHeadPort: BranchHeadPort,
    private val handoffEvidenceStore: HandoffEvidenceStore,
    private val headFreshnessValidator: HeadFreshnessValidator,
    private val clock: Clock,
) {

    sealed interface HandoffVerificationResult {
        data class Ready(val evidence: HandoffEvidence) : HandoffVerificationResult
        data class Incomplete(val missingItems: List<String>) : HandoffVerificationResult
    }

    suspend operator fun invoke(
        task: ManualTask,
        submissionId: String,
        zipSha256: String,
        uploadAuditRef: String,
        transitionAuditRef: String,
        uploadIdempotencyKey: String,
        transitionIdempotencyKey: String,
        observedStatus: TaskStatus,
    ): HandoffVerificationResult {
        val missing = mutableListOf<String>()
        if (!task.hasOrderId) missing += "work-order ID"
        if (submissionId.isBlank()) missing += "submissionId"
        if (!zipSha256.matches(Regex("^[0-9a-fA-F]{64}$"))) missing += "zipSha256"
        if (uploadAuditRef.isBlank()) missing += "upload auditRef"
        if (transitionAuditRef.isBlank()) missing += "transition auditRef"
        // The handoff state is defined by what the console can verify, and the task status
        // read back from Gateway A is one of those facts: `completed` is reachable only
        // through a `SET_DONE` that Gateway A accepted against a seal it cross-checked
        // (assessment §3.2). A handoff is never declared ready on a task the gateway still
        // reports as in flight (I-06).
        if (observedStatus != TaskStatus.COMPLETED) {
            missing += "verified completed task status (read back as '${observedStatus.wireValue}')"
        }

        val liveHead = branchHeadPort.fetchLiveHead(task.branch).getOrNull()?.headSha
        val verifiedHead = if (liveHead != null && headFreshnessValidator.isFullSha(liveHead)) {
            liveHead
        } else {
            missing += "verified live 40-char headSha"
            task.headSha
        }

        if (missing.isNotEmpty()) {
            return HandoffVerificationResult.Incomplete(missing)
        }

        val evidence = HandoffEvidence(
            bundleId = UUID.randomUUID().toString(),
            taskId = task.id,
            planId = task.planId,
            orderId = task.orderId,
            branch = task.branch,
            verifiedHeadSha = verifiedHead,
            submissionId = submissionId,
            zipSha256 = zipSha256,
            uploadAuditRef = uploadAuditRef,
            transitionAuditRef = transitionAuditRef,
            uploadIdempotencyKey = uploadIdempotencyKey,
            transitionIdempotencyKey = transitionIdempotencyKey,
            observedTaskStatus = observedStatus,
            createdAtEpochMs = clock.nowEpochMillis(),
            readinessState = HandoffEvidence.READY_MARKER,
        )
        handoffEvidenceStore.save(evidence)
        return HandoffVerificationResult.Ready(evidence)
    }
}
