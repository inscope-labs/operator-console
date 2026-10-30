package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Read-only validation of a plan.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class ValidatePlanUseCase(private val planRepository: PlanRepository) {
    suspend operator fun invoke(planId: String): Boolean =
        TODO("SCAFFOLD: run the plan check.")
}
