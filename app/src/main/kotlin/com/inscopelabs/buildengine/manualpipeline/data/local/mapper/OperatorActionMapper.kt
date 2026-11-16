package com.inscopelabs.buildengine.manualpipeline.data.local.mapper

import com.inscopelabs.buildengine.manualpipeline.data.local.entity.OperatorActionEntity
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperatorActionRecord

object OperatorActionMapper {

    fun toDomain(entity: OperatorActionEntity): OperatorActionRecord = OperatorActionRecord(
        id = entity.id,
        taskId = entity.taskId,
        planId = entity.planId,
        actionName = entity.actionName,
        outcomeLabel = entity.outcomeLabel,
        idempotencyKey = entity.idempotencyKey,
        auditRef = entity.auditRef,
        headSha = entity.headSha,
        detail = entity.detail,
        timestampEpochMs = entity.timestampEpochMs,
    )

    fun toEntity(record: OperatorActionRecord): OperatorActionEntity = OperatorActionEntity(
        id = record.id,
        taskId = record.taskId,
        planId = record.planId,
        actionName = record.actionName,
        outcomeLabel = record.outcomeLabel,
        idempotencyKey = record.idempotencyKey,
        auditRef = record.auditRef,
        headSha = record.headSha,
        detail = record.detail,
        timestampEpochMs = record.timestampEpochMs,
    )
}
