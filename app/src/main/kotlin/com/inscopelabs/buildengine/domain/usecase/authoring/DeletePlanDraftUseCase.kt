package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository
import javax.inject.Inject

/**
 * Discards a plan draft (PMA Stage 1).
 *
 * Discarding is local and total: the draft is gone from the device. It never touched the
 * repository, so nothing has to be undone there.
 */
class DeletePlanDraftUseCase @Inject constructor(
    private val repository: PlanDraftRepository,
) {

    suspend operator fun invoke(draftId: String): Boolean = repository.deleteDraft(draftId)
}
