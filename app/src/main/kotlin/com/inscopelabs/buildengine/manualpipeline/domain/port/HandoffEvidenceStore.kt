package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import kotlinx.coroutines.flow.Flow

/**
 * Store for verified `PRE_TRANSFER_HANDOFF_READY` evidence bundles (MP-014).
 */
interface HandoffEvidenceStore {
    fun observeForTask(taskId: String): Flow<List<HandoffEvidence>>
    suspend fun latestForTask(taskId: String): HandoffEvidence?
    suspend fun save(evidence: HandoffEvidence)
}
