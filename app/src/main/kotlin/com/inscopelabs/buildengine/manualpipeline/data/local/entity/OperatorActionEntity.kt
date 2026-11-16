package com.inscopelabs.buildengine.manualpipeline.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Redacted, non-secret operator action history entry (MP-013, I-04).
 */
@Entity(
    tableName = "mp_operator_actions",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["timestampEpochMs"]),
    ],
)
data class OperatorActionEntity(
    @PrimaryKey
    val id: String,
    val taskId: String,
    val planId: String,
    val actionName: String,
    val outcomeLabel: String,
    val idempotencyKey: String? = null,
    val auditRef: String? = null,
    val headSha: String? = null,
    val detail: String,
    val timestampEpochMs: Long,
)
