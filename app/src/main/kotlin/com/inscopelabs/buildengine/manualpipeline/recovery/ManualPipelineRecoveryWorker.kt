package com.inscopelabs.buildengine.manualpipeline.recovery

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inscopelabs.buildengine.util.Logger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * WorkManager lookup-only background recovery worker (I-03).
 *
 * Queries `GET /operations/{id}` via [RecoveryReconciler] for pending/uncertain operations
 * and NEVER sends or resends a write operation. Uses Hilt [EntryPointAccessors] to match
 * the host app's WorkManager setup without requiring `androidx.hilt:hilt-work`.
 */
class ManualPipelineRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecoveryEntryPoint {
        fun recoveryReconciler(): RecoveryReconciler
    }

    override suspend fun doWork(): Result {
        val reconciler = EntryPointAccessors.fromApplication(
            applicationContext,
            RecoveryEntryPoint::class.java,
        ).recoveryReconciler()

        return try {
            val summary = reconciler.reconcileUncertain()
            Logger.i(
                TAG,
                "Manual pipeline recovery pass finished: reconciled=${summary.totalChecked}, pending=${summary.stillPendingCount}",
            )
            if (summary.stillPendingCount > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, "Manual pipeline lookup recovery failed closed without resending", e)
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "manual_pipeline_lookup_recovery"
        private const val TAG = "ManualPipelineRecovery"
    }
}
