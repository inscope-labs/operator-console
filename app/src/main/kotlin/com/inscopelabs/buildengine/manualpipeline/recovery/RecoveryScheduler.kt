package com.inscopelabs.buildengine.manualpipeline.recovery

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.inscopelabs.buildengine.manualpipeline.domain.port.JournalPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules lookup-only [ManualPipelineRecoveryWorker] when uncertain or in-flight intents exist.
 */
@Singleton
class RecoveryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalPort: JournalPort,
) {

    suspend fun scheduleIfPending() {
        if (journalPort.hasPendingIntents()) {
            enqueueLookupRecovery()
        }
    }

    fun enqueueLookupRecovery() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ManualPipelineRecoveryWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ManualPipelineRecoveryWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
