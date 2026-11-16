package com.inscopelabs.buildengine.manualpipeline.data.journal

import com.inscopelabs.buildengine.data.local.JournalFieldCipher
import com.inscopelabs.buildengine.data.local.OperationJournalCodec
import com.inscopelabs.buildengine.data.local.OperationJournalDao
import com.inscopelabs.buildengine.data.local.OperationJournalState
import com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord
import com.inscopelabs.buildengine.manualpipeline.domain.port.JournalPort
import javax.inject.Inject

/**
 * Read adapter over the host's encrypted [OperationJournalDao] using [OperationJournalCodec].
 * Fails closed: entries whose ciphertext cannot be decrypted are omitted or flagged safely.
 */
class ExistingJournalAdapter @Inject constructor(
    private val journalDao: OperationJournalDao,
    private val cipher: JournalFieldCipher,
) : JournalPort {

    override suspend fun findAllRecords(): List<OperationRecord> =
        journalDao.getAll().mapNotNull { entity ->
            runCatching { OperationJournalCodec.decryptRecord(entity, cipher) }
                .getOrNull()
                ?.let(JournalMapper::toDomain)
        }

    override suspend fun findUncertainOrInFlight(): List<OperationRecord> {
        val candidates = journalDao.findByStates(
            OperationRepositoryImpl.RECOVERY_CANDIDATE_STATES + OperationJournalState.RECOVERY_REQUIRED,
        )
        return candidates.mapNotNull { entity ->
            runCatching { OperationJournalCodec.decryptRecord(entity, cipher) }
                .getOrNull()
                ?.let(JournalMapper::toDomain)
        }
    }

    override suspend fun findByKey(idempotencyKey: String): OperationRecord? {
        val entity = journalDao.findByKey(idempotencyKey) ?: return null
        return runCatching { OperationJournalCodec.decryptRecord(entity, cipher) }
            .getOrNull()
            ?.let(JournalMapper::toDomain)
    }

    override suspend fun hasPendingIntents(): Boolean =
        journalDao.findByStates(OperationRepositoryImpl.RECOVERY_CANDIDATE_STATES).isNotEmpty()
}
