package com.inscopelabs.buildengine.manualpipeline.data.local.mapper

import com.inscopelabs.buildengine.manualpipeline.data.local.entity.HandoffEvidenceEntity
import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus

object HandoffEvidenceMapper {

    fun toDomain(entity: HandoffEvidenceEntity): HandoffEvidence = HandoffEvidence(
        bundleId = entity.bundleId,
        taskId = entity.taskId,
        planId = entity.planId,
        orderId = entity.orderId,
        branch = entity.branch,
        verifiedHeadSha = entity.verifiedHeadSha,
        submissionId = entity.submissionId,
        zipSha256 = entity.zipSha256,
        uploadAuditRef = entity.uploadAuditRef,
        transitionAuditRef = entity.transitionAuditRef,
        uploadIdempotencyKey = entity.uploadIdempotencyKey,
        transitionIdempotencyKey = entity.transitionIdempotencyKey,
        observedTaskStatus = TaskStatus.fromWire(entity.observedTaskStatus),
        createdAtEpochMs = entity.createdAtEpochMs,
        readinessState = entity.readinessState,
    )

    fun toEntity(evidence: HandoffEvidence): HandoffEvidenceEntity = HandoffEvidenceEntity(
        bundleId = evidence.bundleId,
        taskId = evidence.taskId,
        planId = evidence.planId,
        orderId = evidence.orderId,
        branch = evidence.branch,
        verifiedHeadSha = evidence.verifiedHeadSha,
        submissionId = evidence.submissionId,
        zipSha256 = evidence.zipSha256,
        uploadAuditRef = evidence.uploadAuditRef,
        transitionAuditRef = evidence.transitionAuditRef,
        uploadIdempotencyKey = evidence.uploadIdempotencyKey,
        transitionIdempotencyKey = evidence.transitionIdempotencyKey,
        observedTaskStatus = evidence.observedTaskStatus.wireValue,
        createdAtEpochMs = evidence.createdAtEpochMs,
        readinessState = evidence.readinessState,
    )
}
