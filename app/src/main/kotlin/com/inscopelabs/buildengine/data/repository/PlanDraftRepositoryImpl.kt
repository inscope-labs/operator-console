package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.data.local.PlanDraftStore
import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.model.PlanDraftSummary
import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plan drafts, as they are stored on the device.
 *
 * The layer where storage stops: the draft document crosses as a [PlanDraft] and nothing about
 * how it is kept on the device reaches the domain.
 */
@Singleton
class PlanDraftRepositoryImpl @Inject constructor(
    private val store: PlanDraftStore,
) : PlanDraftRepository {

    override suspend fun listDrafts(): List<PlanDraftSummary> =
        store.listDrafts().map { draft ->
            PlanDraftSummary(
                draftId = draft.draftId,
                planId = draft.planId,
                title = draft.title,
                updatedAtEpochMs = draft.updatedAtEpochMs,
                revision = draft.revision,
                phaseCount = draft.phases.size,
                stageCount = draft.stages.size,
                taskCount = draft.tasks.size,
                isPlanNumberReserved = draft.isPlanNumberReserved,
            )
        }

    override suspend fun loadDraft(draftId: String): PlanDraft? = store.read(draftId)

    override suspend fun saveDraft(draft: PlanDraft): PlanDraft {
        store.write(draft)
        return draft
    }

    override suspend fun deleteDraft(draftId: String): Boolean = store.delete(draftId)
}
