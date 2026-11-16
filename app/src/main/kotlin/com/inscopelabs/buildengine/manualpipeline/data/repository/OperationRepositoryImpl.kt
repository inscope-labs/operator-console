package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.ExecutorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.JournalPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperationLookupPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Module facade for reading encrypted journal records and running lookup-only recovery.
 */
@Singleton
class OperationRepositoryImpl @Inject constructor(
    private val journalPort: JournalPort,
    private val operationLookupPort: OperationLookupPort,
    private val executorPort: ExecutorPort,
) {
    suspend fun getAllJournalRecords(): List<OperationRecord> = journalPort.findAllRecords()

    suspend fun getUncertainRecords(): List<OperationRecord> = journalPort.findUncertainOrInFlight()

    suspend fun lookupGatewayStatus(idempotencyKey: String): Result<RecoveryStatus> =
        operationLookupPort.lookupOperation(idempotencyKey)

    suspend fun resolveOperation(idempotencyKey: String): OperationOutcome =
        executorPort.lookupOperation(idempotencyKey)

    suspend fun resolveAllPending(): Map<String, OperationOutcome> =
        executorPort.recoverUncertainOperations()
}
