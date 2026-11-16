package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.port.Clock
import com.inscopelabs.buildengine.manualpipeline.domain.port.RedactorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordStore
import java.util.UUID
import javax.inject.Inject

/**
 * MP-009: Persists non-secret module workflow metadata (phase, status, headSha, idempotencyKey,
 * auditRef, zipSha256) into the module's Room store (I-04). Rejects any secret pattern before write.
 */
class RecordWorkflowMetadataUseCase @Inject constructor(
    private val store: WorkflowRecordStore,
    private val redactor: RedactorPort,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        taskId: String,
        planId: String,
        orderId: String,
        branch: String,
        headSha: String,
        stageId: String,
        statusWire: String,
        phaseLabel: String,
        idempotencyKey: String? = null,
        auditRef: String? = null,
        submissionId: String? = null,
        zipSha256: String? = null,
    ) {
        val candidateFields = listOfNotNull(
            taskId,
            planId,
            orderId,
            branch,
            headSha,
            stageId,
            statusWire,
            phaseLabel,
            idempotencyKey,
            auditRef,
            submissionId,
            zipSha256,
        )
        require(candidateFields.none { redactor.containsForbidden(it) }) {
            "Refusing to persist secret/capability material in workflow metadata store (I-04)"
        }

        store.saveRecord(
            WorkflowRecordSnapshot(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                planId = planId,
                orderId = orderId,
                branch = branch,
                headSha = headSha,
                stageId = stageId,
                statusWire = statusWire,
                phaseLabel = phaseLabel,
                idempotencyKey = idempotencyKey,
                auditRef = auditRef,
                submissionId = submissionId,
                zipSha256 = zipSha256,
                updatedAtEpochMs = clock.nowEpochMillis(),
            ),
        )
    }
}
