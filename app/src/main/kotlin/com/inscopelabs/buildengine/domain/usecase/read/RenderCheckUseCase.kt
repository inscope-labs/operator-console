package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Confirms that the generated views for a plan are current.
 *
 * A read-only check. The issues are carried for the same reason the validation's
 * errors are: a report that says "not current" without saying which view is stale
 * is a report the operator cannot act on.
 */
class RenderCheckUseCase(private val planRepository: PlanRepository) {

    suspend operator fun invoke(planId: String): AppResult<RenderCheck> =
        planRepository.renderCheck(planId)
}
