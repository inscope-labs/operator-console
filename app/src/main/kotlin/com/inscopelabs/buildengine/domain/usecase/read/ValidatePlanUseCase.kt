package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Read-only validation of a plan.
 *
 * It changes nothing and needs no capability. The errors and the warnings are
 * carried rather than reduced to a boolean, because "invalid" without the reasons
 * gives the operator nothing to act on.
 */
class ValidatePlanUseCase(private val planRepository: PlanRepository) {

    suspend operator fun invoke(planId: String): AppResult<PlanValidation> =
        planRepository.validate(planId)
}
