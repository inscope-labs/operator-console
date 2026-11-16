package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.data.local.dao.OperatorActionDao
import com.inscopelabs.buildengine.manualpipeline.data.local.mapper.OperatorActionMapper
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperatorActionRecord
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperatorActionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class OperatorActionRepositoryImpl @Inject constructor(
    private val dao: OperatorActionDao,
) : OperatorActionStore {

    override fun observeActionsForTask(taskId: String): Flow<List<OperatorActionRecord>> =
        dao.observeForTask(taskId).map { list -> list.map(OperatorActionMapper::toDomain) }

    override fun observeAllActions(): Flow<List<OperatorActionRecord>> =
        dao.observeAll().map { list -> list.map(OperatorActionMapper::toDomain) }

    override suspend fun recordAction(record: OperatorActionRecord) {
        dao.insert(OperatorActionMapper.toEntity(record))
    }
}
