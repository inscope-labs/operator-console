package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Lists the plans of record.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class GetPlansUseCase(private val planRepository: PlanRepository) {
    suspend operator fun invoke(): List<Plan> = TODO("SCAFFOLD: return the plan list.")
}
