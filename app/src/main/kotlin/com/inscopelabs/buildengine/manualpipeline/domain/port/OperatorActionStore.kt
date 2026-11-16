package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperatorActionRecord
import kotlinx.coroutines.flow.Flow

/**
 * Store for module-local non-secret operator action history (MP-013).
 */
interface OperatorActionStore {
    fun observeActionsForTask(taskId: String): Flow<List<OperatorActionRecord>>
    fun observeAllActions(): Flow<List<OperatorActionRecord>>
    suspend fun recordAction(record: OperatorActionRecord)
}
