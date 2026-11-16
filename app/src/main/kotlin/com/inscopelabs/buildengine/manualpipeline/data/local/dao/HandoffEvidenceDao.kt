package com.inscopelabs.buildengine.manualpipeline.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.HandoffEvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HandoffEvidenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HandoffEvidenceEntity)

    @Query("SELECT * FROM mp_handoff_evidence WHERE taskId = :taskId ORDER BY createdAtEpochMs DESC")
    fun observeForTask(taskId: String): Flow<List<HandoffEvidenceEntity>>

    @Query("SELECT * FROM mp_handoff_evidence WHERE taskId = :taskId ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun latestForTask(taskId: String): HandoffEvidenceEntity?
}
