package com.inscopelabs.buildengine.manualpipeline.data.local.mapper

import com.inscopelabs.buildengine.manualpipeline.data.local.entity.WorkflowRecordEntity
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordSnapshot

object WorkflowRecordMapper {

    fun toDomain(entity: WorkflowRecordEntity): WorkflowRecordSnapshot = WorkflowRecordSnapshot(
        id = entity.id,
        taskId = entity.taskId,
        planId = entity.planId,
        orderId = entity.orderId,
        branch = entity.branch,
        headSha = entity.headSha,
        stageId = entity.stageId,
        statusWire = entity.statusWire,
        phaseLabel = entity.phaseLabel,
        idempotencyKey = entity.idempotencyKey,
        auditRef = entity.auditRef,
        submissionId = entity.submissionId,
        zipSha256 = entity.zipSha256,
        updatedAtEpochMs = entity.updatedAtEpochMs,
    )

    fun toEntity(snapshot: WorkflowRecordSnapshot): WorkflowRecordEntity = WorkflowRecordEntity(
        id = snapshot.id,
        taskId = snapshot.taskId,
        planId = snapshot.planId,
        orderId = snapshot.orderId,
        branch = snapshot.branch,
        headSha = snapshot.headSha,
        stageId = snapshot.stageId,
        statusWire = snapshot.statusWire,
        phaseLabel = snapshot.phaseLabel,
        idempotencyKey = snapshot.idempotencyKey,
        auditRef = snapshot.auditRef,
        submissionId = snapshot.submissionId,
        zipSha256 = snapshot.zipSha256,
        updatedAtEpochMs = snapshot.updatedAtEpochMs,
    )
}
