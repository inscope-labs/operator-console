package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository
import javax.inject.Inject

/**
 * Saves a plan draft (PMA Stage 1).
 *
 * Saving bumps the draft's revision and stamps it. The revision is the local optimistic-concurrency
 * token: it tells the operator that what they are looking at is the copy they saved, and it is what
 * a submission would have to be bound to.
 *
 * Saving a draft changes nothing outside the device. A draft is not a plan until a repository
 * change carrying it has been created and merged.
 */
class SavePlanDraftUseCase @Inject constructor(
    private val repository: PlanDraftRepository,
) {

    suspend operator fun invoke(draft: PlanDraft): PlanDraft {
        val stored = draft.copy(
            revision = draft.revision + 1,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        return repository.saveDraft(stored)
    }
}
