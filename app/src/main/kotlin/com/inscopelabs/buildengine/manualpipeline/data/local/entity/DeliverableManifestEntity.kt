package com.inscopelabs.buildengine.manualpipeline.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Deliverable manifest digest entry associated with a task submission (I-04).
 */
@Entity(
    tableName = "mp_deliverable_manifests",
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["submissionId"]),
    ],
)
data class DeliverableManifestEntity(
    @PrimaryKey
    val id: String,
    val submissionId: String,
    val taskId: String,
    val relativePath: String,
    val requiredItem: Boolean,
    val supplied: Boolean,
    val sizeBytes: Long? = null,
    val fileSha256: String? = null,
    val updatedAtEpochMs: Long,
)
