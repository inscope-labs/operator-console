package com.inscopelabs.buildengine.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl
import com.inscopelabs.buildengine.util.Logger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Resolves journaled operations in an uncertain state on app launch / network recovery (T3-203).
 *
 * Performs read-only `GET /operations/{id}` lookups ONLY via
 * [OperationRepositoryImpl.recoverUncertainOperations]; never sends or resends a write.
 * Uses Hilt [EntryPoint] (`EntryPointAccessors`) instead of `@HiltWorker`.
 */
class OperationRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface OperationRecoveryEntryPoint {
        fun operationRepository(): OperationRepositoryImpl
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            OperationRecoveryEntryPoint::class.java,
        ).operationRepository()

        return try {
            val summary = repository.recoverUncertainOperations()
            Logger.i(
                TAG,
                "Recovery pass complete: resolved=${summary.resolvedCount}, pendingRetry=${summary.retryCount}",
            )
            if (summary.retryCount > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Logger.e(TAG, "Recovery pass failed closed without resending writes", e)
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "operation_journal_launch_recovery"
        private const val TAG = "OperationRecoveryWorker"

        fun enqueueLaunchRecovery(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<OperationRecoveryWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
