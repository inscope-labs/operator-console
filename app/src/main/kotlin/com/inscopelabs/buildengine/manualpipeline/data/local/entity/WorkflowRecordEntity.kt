package com.inscopelabs.buildengine.manualpipeline.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Module-local non-secret workflow metadata row (I-04).
 * Contains zero capabilities, signatures, nonces, or bearer credentials.
 */
@Entity(
    tableName = "mp_workflow_records",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["updatedAtEpochMs"]),
    ],
)
data class WorkflowRecordEntity(
    @PrimaryKey
    val id: String,
    val taskId: String,
    val planId: String,
    val orderId: String,
    val branch: String,
    val headSha: String,
    val stageId: String,
    val statusWire: String,
    val phaseLabel: String,
    val idempotencyKey: String? = null,
    val auditRef: String? = null,
    val submissionId: String? = null,
    val zipSha256: String? = null,
    val updatedAtEpochMs: Long,
)
