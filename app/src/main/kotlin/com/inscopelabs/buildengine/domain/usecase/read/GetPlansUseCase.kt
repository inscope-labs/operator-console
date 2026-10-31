package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Lists the plans of record.
 *
 * Design review §12: the read-only commands are half the operator surface, and the
 * half that needs no write capability. Serving them first is the recommended
 * sequence — the console can be useful to an operator before it can write anything.
 *
 * The use case is thin on purpose. It is the seam the UI depends on: the screen
 * asks for plans and never learns that they arrive as JSON over HTTP.
 */
class GetPlansUseCase(private val planRepository: PlanRepository) {

    suspend operator fun invoke(): AppResult<List<Plan>> = planRepository.getPlans()
}
