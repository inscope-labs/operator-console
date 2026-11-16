package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.data.local.dao.WorkflowRecordDao
import com.inscopelabs.buildengine.manualpipeline.data.local.mapper.WorkflowRecordMapper
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class WorkflowRecordRepositoryImpl @Inject constructor(
    private val dao: WorkflowRecordDao,
) : WorkflowRecordStore {

    override fun observeRecordsForTask(taskId: String): Flow<List<WorkflowRecordSnapshot>> =
        dao.observeForTask(taskId).map { list -> list.map(WorkflowRecordMapper::toDomain) }

    override suspend fun latestForTask(taskId: String): WorkflowRecordSnapshot? =
        dao.latestForTask(taskId)?.let(WorkflowRecordMapper::toDomain)

    override suspend fun saveRecord(record: WorkflowRecordSnapshot) {
        dao.upsert(WorkflowRecordMapper.toEntity(record))
    }
}
