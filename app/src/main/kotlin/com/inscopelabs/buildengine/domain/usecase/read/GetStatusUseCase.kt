package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Comparative repository state, open branches and pull-request status.
 *
 * This is the document the operator reads before approving anything, and it says
 * the two things that would make the approval fail: whether the engine is halted,
 * and which branches the plan currently has open. A branch that has moved is a
 * mint the gateway will refuse (docs/threat-model.md, V7).
 */
class GetStatusUseCase(private val planRepository: PlanRepository) {

    suspend operator fun invoke(planId: String): AppResult<PlanStatus> =
        planRepository.getStatus(planId)
}
