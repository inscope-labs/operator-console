package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository
import javax.inject.Inject

/**
 * Loads one plan draft (PMA Stage 1).
 *
 * A draft that is not on the device any more is null, not an empty draft: an editor opened on a
 * blank plan would look like a plan the operator wrote and lost.
 */
class LoadPlanDraftUseCase @Inject constructor(
    private val repository: PlanDraftRepository,
) {

    suspend operator fun invoke(draftId: String): PlanDraft? = repository.loadDraft(draftId)
}
