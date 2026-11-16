package com.inscopelabs.buildengine.manualpipeline.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Module-local non-secret workflow metadata store (I-04).
 */
interface WorkflowRecordStore {
    fun observeRecordsForTask(taskId: String): Flow<List<WorkflowRecordSnapshot>>
    suspend fun latestForTask(taskId: String): WorkflowRecordSnapshot?
    suspend fun saveRecord(record: WorkflowRecordSnapshot)
}

data class WorkflowRecordSnapshot(
    val id: String,
    val taskId: String,
    val planId: String,
    val orderId: String,
    val branch: String,
    val headSha: String,
    val stageId: String,
    val statusWire: String,
    val phaseLabel: String,
    val idempotencyKey: String?,
    val auditRef: String?,
    val submissionId: String?,
    val zipSha256: String?,
    val updatedAtEpochMs: Long,
)
