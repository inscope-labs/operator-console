package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord

/**
 * Port reading non-secret operation records from the host's encrypted Room journal.
 */
interface JournalPort {
    suspend fun findAllRecords(): List<OperationRecord>
    suspend fun findUncertainOrInFlight(): List<OperationRecord>
    suspend fun findByKey(idempotencyKey: String): OperationRecord?
    suspend fun hasPendingIntents(): Boolean
}
