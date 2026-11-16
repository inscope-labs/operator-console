package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.data.local.dao.WorkflowRecordDao
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.DeliverableManifestEntity
import com.inscopelabs.buildengine.manualpipeline.domain.model.Deliverable
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.port.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores and retrieves non-secret deliverable manifest entries per task submission.
 */
@Singleton
class DeliverableRepositoryImpl @Inject constructor(
    private val workflowRecordDao: WorkflowRecordDao,
    private val clock: Clock,
) {
    suspend fun saveManifest(manifest: SubmissionManifest, deliverables: List<Deliverable>) {
        val now = clock.nowEpochMillis()
        val entities = deliverables.mapIndexed { idx, item ->
            DeliverableManifestEntity(
                id = "${manifest.submissionId}:$idx:${item.relativePath}",
                submissionId = manifest.submissionId,
                taskId = manifest.taskId,
                relativePath = item.relativePath,
                requiredItem = item.required,
                supplied = item.supplied,
                sizeBytes = item.suppliedSizeBytes,
                fileSha256 = item.suppliedSha256,
                updatedAtEpochMs = now,
            )
        }
        workflowRecordDao.upsertDeliverables(entities)
    }

    suspend fun loadForTask(taskId: String): List<Deliverable> =
        workflowRecordDao.getDeliverablesForTask(taskId).map { entity ->
            Deliverable(
                relativePath = entity.relativePath,
                required = entity.requiredItem,
                supplied = entity.supplied,
                suppliedSizeBytes = entity.sizeBytes,
                suppliedSha256 = entity.fileSha256,
            )
        }
}
