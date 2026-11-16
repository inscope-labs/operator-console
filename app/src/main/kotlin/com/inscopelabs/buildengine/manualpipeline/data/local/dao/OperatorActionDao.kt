package com.inscopelabs.buildengine.manualpipeline.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inscopelabs.buildengine.manualpipeline.data.local.entity.OperatorActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperatorActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OperatorActionEntity)

    @Query("SELECT * FROM mp_operator_actions WHERE taskId = :taskId ORDER BY timestampEpochMs DESC")
    fun observeForTask(taskId: String): Flow<List<OperatorActionEntity>>

    @Query("SELECT * FROM mp_operator_actions ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeAll(limit: Int = 200): Flow<List<OperatorActionEntity>>
}
