package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.domain.model.Plan

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
interface PlanRepository {

    suspend fun getPlans(): List<Plan>

    suspend fun getPlan(planId: String): Plan

    suspend fun validate(planId: String): Boolean

    suspend fun renderIsCurrent(planId: String): Boolean
}
