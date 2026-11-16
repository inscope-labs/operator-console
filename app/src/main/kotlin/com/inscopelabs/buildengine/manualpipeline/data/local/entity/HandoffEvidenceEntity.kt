package com.inscopelabs.buildengine.manualpipeline.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted `PRE_TRANSFER_HANDOFF_READY` evidence bundle row (MP-014, I-04).
 */
@Entity(
    tableName = "mp_handoff_evidence",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["createdAtEpochMs"]),
    ],
)
data class HandoffEvidenceEntity(
    @PrimaryKey
    val bundleId: String,
    val taskId: String,
    val planId: String,
    val orderId: String,
    val branch: String,
    val verifiedHeadSha: String,
    val submissionId: String,
    val zipSha256: String,
    val uploadAuditRef: String,
    val transitionAuditRef: String,
    val uploadIdempotencyKey: String,
    val transitionIdempotencyKey: String,
    val observedTaskStatus: String,
    val createdAtEpochMs: Long,
    val readinessState: String,
)
