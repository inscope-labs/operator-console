package com.inscopelabs.buildengine.manualpipeline.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.inscopelabs.buildengine.manualpipeline.data.local.dao.HandoffEvidenceDao
import com.inscopelabs.buildengine.manualpipeline.data.local.dao.OperatorActionDao
import com.inscopelabs.buildengine.manualpipeline.data.local.dao.WorkflowRecordDao
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.DeliverableManifestEntity
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.HandoffEvidenceEntity
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.OperatorActionEntity
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.WorkflowRecordEntity

/**
 * Isolated Room database for non-secret `manualpipeline` metadata (I-04).
 * Separated completely from the host encrypted `OperationJournalDatabase`.
 */
@Database(
    entities = [
        WorkflowRecordEntity::class,
        OperatorActionEntity::class,
        HandoffEvidenceEntity::class,
        DeliverableManifestEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ManualPipelineDatabase : RoomDatabase() {
    abstract fun workflowRecordDao(): WorkflowRecordDao
    abstract fun operatorActionDao(): OperatorActionDao
    abstract fun handoffEvidenceDao(): HandoffEvidenceDao

    companion object {
        const val DATABASE_NAME: String = "manual_pipeline_metadata.db"
    }
}
