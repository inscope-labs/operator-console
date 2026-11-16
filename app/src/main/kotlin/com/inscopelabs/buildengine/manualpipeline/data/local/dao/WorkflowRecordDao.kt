package com.inscopelabs.buildengine.manualpipeline.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.DeliverableManifestEntity
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.WorkflowRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WorkflowRecordEntity)

    @Query("SELECT * FROM mp_workflow_records WHERE taskId = :taskId ORDER BY updatedAtEpochMs DESC")
    fun observeForTask(taskId: String): Flow<List<WorkflowRecordEntity>>

    @Query("SELECT * FROM mp_workflow_records WHERE taskId = :taskId ORDER BY updatedAtEpochMs DESC LIMIT 1")
    suspend fun latestForTask(taskId: String): WorkflowRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeliverables(items: List<DeliverableManifestEntity>)

    @Query("SELECT * FROM mp_deliverable_manifests WHERE taskId = :taskId ORDER BY relativePath ASC")
    suspend fun getDeliverablesForTask(taskId: String): List<DeliverableManifestEntity>
}
