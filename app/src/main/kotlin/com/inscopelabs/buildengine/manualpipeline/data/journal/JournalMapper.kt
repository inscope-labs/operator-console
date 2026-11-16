package com.inscopelabs.buildengine.manualpipeline.data.journal

import com.inscopelabs.buildengine.data.local.DecryptedJournalRecord
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord

object JournalMapper {

    fun toDomain(record: DecryptedJournalRecord): OperationRecord = OperationRecord(
        idempotencyKey = record.idempotencyKey,
        operationName = record.intent.operation.name,
        planId = record.intent.plan,
        taskId = record.intent.task,
        branch = record.intent.branch,
        expectedHead = record.intent.expectedHead,
        state = record.state.name,
        auditRef = record.auditRef,
        resultHeadSha = record.resultHeadSha,
        lastErrorCode = record.lastErrorCode,
        createdAtEpochMs = record.createdAtEpochMs,
        updatedAtEpochMs = record.updatedAtEpochMs,
    )
}
