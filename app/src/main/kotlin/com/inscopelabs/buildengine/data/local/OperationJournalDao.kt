package com.inscopelabs.buildengine.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room DAO for [OperationJournalEntity] (T3-202, PR3-011).
 */
@Dao
interface OperationJournalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: OperationJournalEntity)

    /**
     * Atomically commits a new [OperationJournalState.INTENT_RECORDED] journal row unless an
     * unresolved entry with the same [OperationJournalEntity.intentFingerprint] already exists
     * in [activeStates]. Returns the committed or existing active row.
     */
    @Transaction
    suspend fun commitIntentIfNoActive(
        entry: OperationJournalEntity,
        activeStates: List<OperationJournalState>,
    ): OperationJournalEntity {
        require(entry.state == OperationJournalState.INTENT_RECORDED) {
            "Initial journal entry must start in INTENT_RECORDED, got ${entry.state}"
        }
        val existing = findByIntentFingerprintAndStates(entry.intentFingerprint, activeStates)
        if (existing != null) {
            return existing
        }
        insert(entry)
        return findByKey(entry.idempotencyKey)
            ?: throw IllegalStateException(
                "Journal transaction did not persist record for key=${entry.idempotencyKey}",
            )
    }

    /**
     * Atomic compare-and-set update guarded by [expectedFromState]. Callers must go through
     * [transitionState] so transitions are validated before executing SQL.
     */
    @Query(
        "UPDATE operation_journal " +
            "SET state = :toState, " +
            "updatedAtEpochMs = :updatedAtEpochMs, " +
            "encryptedAuditRef = COALESCE(:encryptedAuditRef, encryptedAuditRef), " +
            "encryptedResultHeadSha = COALESCE(:encryptedResultHeadSha, encryptedResultHeadSha), " +
            "encryptedLastErrorCode = COALESCE(:encryptedLastErrorCode, encryptedLastErrorCode) " +
            "WHERE idempotencyKey = :idempotencyKey AND state = :expectedFromState",
    )
    suspend fun updateStateIfCurrent(
        idempotencyKey: String,
        expectedFromState: OperationJournalState,
        toState: OperationJournalState,
        updatedAtEpochMs: Long,
        encryptedAuditRef: String?,
        encryptedResultHeadSha: String?,
        encryptedLastErrorCode: String?,
    ): Int

    /**
     * Validates the transition [expectedFromState] -> [toState] and atomically updates the
     * persisted row only if its current committed state equals [expectedFromState].
     */
    @Transaction
    suspend fun transitionState(
        idempotencyKey: String,
        expectedFromState: OperationJournalState,
        toState: OperationJournalState,
        updatedAtEpochMs: Long,
        encryptedAuditRef: String? = null,
        encryptedResultHeadSha: String? = null,
        encryptedLastErrorCode: String? = null,
    ): Boolean {
        OperationJournalState.requireValidTransition(expectedFromState, toState)
        return updateStateIfCurrent(
            idempotencyKey = idempotencyKey,
            expectedFromState = expectedFromState,
            toState = toState,
            updatedAtEpochMs = updatedAtEpochMs,
            encryptedAuditRef = encryptedAuditRef,
            encryptedResultHeadSha = encryptedResultHeadSha,
            encryptedLastErrorCode = encryptedLastErrorCode,
        ) == 1
    }

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
