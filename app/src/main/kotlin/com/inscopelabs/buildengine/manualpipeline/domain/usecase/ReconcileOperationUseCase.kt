package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryState
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.ExecutorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.JournalPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperationLookupPort
import javax.inject.Inject

/**
 * MP-010: Lookup-only recovery reconciliation (`GET /operations/{id}`).
 * Never auto-resends a write operation (I-03, T3-203).
 */
class ReconcileOperationUseCase @Inject constructor(
    private val journalPort: JournalPort,
    private val operationLookupPort: OperationLookupPort,
    private val executorPort: ExecutorPort,
    private val recordOperatorActionUseCase: RecordOperatorActionUseCase,
) {

    suspend fun loadRecoveryQueue(): List<OperationRecord> =
        journalPort.findUncertainOrInFlight()

    suspend fun reconcileByKey(idempotencyKey: String): RecoveryStatus {
        val journalRecord = journalPort.findByKey(idempotencyKey)
        val outcome = executorPort.lookupOperation(idempotencyKey)
        val status = when (outcome) {
            is OperationOutcome.Succeeded -> RecoveryStatus(
                idempotencyKey = idempotencyKey,
                state = RecoveryState.RESOLVED_SUCCEEDED,
                gatewayOperationStatus = "completed",
                auditRef = outcome.auditRef,
                headSha = outcome.headSha,
                detail = "Verified completed on Gateway A and matched live branch head.",
            )
            is OperationOutcome.Refused -> RecoveryStatus(
                idempotencyKey = idempotencyKey,
                state = RecoveryState.RESOLVED_REFUSED,
                gatewayOperationStatus = "refused",
                auditRef = outcome.auditRef,
                errorCode = outcome.code,
                detail = outcome.message,
            )
            is OperationOutcome.Conflict -> RecoveryStatus(
                idempotencyKey = idempotencyKey,
                state = RecoveryState.RESOLVED_CONFLICT,
                gatewayOperationStatus = "refused",
                auditRef = outcome.auditRef,
                headSha = outcome.liveHeadSha,
                errorCode = outcome.code,
                detail = outcome.message,
            )
            is OperationOutcome.Timeout,
            is OperationOutcome.UncertainCompletion -> {
                operationLookupPort.lookupOperation(idempotencyKey).getOrElse {
                    RecoveryStatus(
                        idempotencyKey = idempotencyKey,
                        state = RecoveryState.PENDING_RETRY,
                        gatewayOperationStatus = "pending",
                        detail = "Still pending/uncertain on Gateway A; read-only retry scheduled.",
                    )
                }
            }
            is OperationOutcome.RecoveryRequired -> RecoveryStatus(
                idempotencyKey = idempotencyKey,
                state = RecoveryState.MANUAL_REVIEW_REQUIRED,
                gatewayOperationStatus = "failed",
                auditRef = outcome.auditRef,
                errorCode = outcome.code,
                detail = outcome.message,
            )
        }

        if (journalRecord != null) {
            recordOperatorActionUseCase(
                taskId = journalRecord.taskId,
                planId = journalRecord.planId,
                actionName = "LOOKUP_RECOVERY",
                outcomeLabel = status.state.name,
                detail = "Lookup reconciliation for $idempotencyKey: ${status.detail}",
                idempotencyKey = idempotencyKey,
                auditRef = status.auditRef,
                headSha = status.headSha,
            )
        }
        return status
    }

    suspend fun reconcileAllPending(): Map<String, OperationOutcome> =
        executorPort.recoverUncertainOperations()
}
