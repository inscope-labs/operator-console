package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class PlanRepositoryImpl(
    private val gateway: GatewayRepository,
) : PlanRepository {

    override suspend fun getPlans(): List<Plan> = TODO("SCAFFOLD: map the gateway DTOs.")

    override suspend fun getPlan(planId: String): Plan =
        TODO("SCAFFOLD: map one plan.")

    override suspend fun validate(planId: String): Boolean =
        TODO("SCAFFOLD: plan check (design review §12).")

    override suspend fun renderIsCurrent(planId: String): Boolean =
        TODO("SCAFFOLD: render --check (design review §12).")
}
