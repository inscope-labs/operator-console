package com.inscopelabs.buildengine.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Room DAO for [OperationJournalEntity] (T3-202).
 */
@Dao
interface OperationJournalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: OperationJournalEntity)

    @Update
    suspend fun update(entry: OperationJournalEntity)

    @Query("SELECT * FROM operation_journal WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun findByKey(idempotencyKey: String): OperationJournalEntity?

    @Query(
        "SELECT * FROM operation_journal " +
            "WHERE intentFingerprint = :intentFingerprint " +
            "AND state IN (:states) " +
            "ORDER BY createdAtEpochMs DESC LIMIT 1",
    )
    suspend fun findByIntentFingerprintAndStates(
        intentFingerprint: String,
        states: List<OperationJournalState>,
    ): OperationJournalEntity?

    @Query("SELECT * FROM operation_journal WHERE state IN (:states) ORDER BY createdAtEpochMs ASC")
    suspend fun findByStates(states: List<OperationJournalState>): List<OperationJournalEntity>

    @Query("SELECT * FROM operation_journal ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<OperationJournalEntity>
}
