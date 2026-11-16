package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.data.local.dao.HandoffEvidenceDao
import com.inscopelabs.buildengine.manualpipeline.data.local.mapper.HandoffEvidenceMapper
import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import com.inscopelabs.buildengine.manualpipeline.domain.port.HandoffEvidenceStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HandoffRepositoryImpl @Inject constructor(
    private val dao: HandoffEvidenceDao,
) : HandoffEvidenceStore {

    override fun observeForTask(taskId: String): Flow<List<HandoffEvidence>> =
        dao.observeForTask(taskId).map { list -> list.map(HandoffEvidenceMapper::toDomain) }

    override suspend fun latestForTask(taskId: String): HandoffEvidence? =
        dao.latestForTask(taskId)?.let(HandoffEvidenceMapper::toDomain)

    override suspend fun save(evidence: HandoffEvidence) {
        dao.upsert(HandoffEvidenceMapper.toEntity(evidence))
    }
}
