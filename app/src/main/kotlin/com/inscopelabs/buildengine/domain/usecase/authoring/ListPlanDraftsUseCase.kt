package com.inscopelabs.buildengine.domain.usecase.authoring

import com.inscopelabs.buildengine.domain.model.PlanDraftSummary
import com.inscopelabs.buildengine.domain.repository.PlanDraftRepository
import javax.inject.Inject

/**
 * Lists the plan drafts on the device (PMA Stage 1).
 *
 * Most recently updated first: the draft an operator was last working on is the one they are
 * most likely to want.
 */
class ListPlanDraftsUseCase @Inject constructor(
    private val repository: PlanDraftRepository,
) {

    suspend operator fun invoke(): List<PlanDraftSummary> =
        repository.listDrafts().sortedByDescending { it.updatedAtEpochMs }
}
