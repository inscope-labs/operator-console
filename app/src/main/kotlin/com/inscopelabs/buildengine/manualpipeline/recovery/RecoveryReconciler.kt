package com.inscopelabs.buildengine.manualpipeline.recovery

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ReconcileOperationUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only reconciliation coordinator executed from WorkManager or the manual Recovery screen.
 */
@Singleton
class RecoveryReconciler @Inject constructor(
    private val reconcileOperationUseCase: ReconcileOperationUseCase,
) {

    data class ReconciliationSummary(
        val totalChecked: Int,
        val stillPendingCount: Int,
        val outcomes: Map<String, OperationOutcome>,
    )

    suspend fun reconcileUncertain(): ReconciliationSummary {
        val outcomes = reconcileOperationUseCase.reconcileAllPending()
        val pendingCount = outcomes.values.count {
            it is OperationOutcome.Timeout || it is OperationOutcome.UncertainCompletion
        }
        return ReconciliationSummary(
            totalChecked = outcomes.size,
            stillPendingCount = pendingCount,
            outcomes = outcomes,
        )
    }

    suspend fun reconcileSingle(idempotencyKey: String): RecoveryStatus =
        reconcileOperationUseCase.reconcileByKey(idempotencyKey)
}
