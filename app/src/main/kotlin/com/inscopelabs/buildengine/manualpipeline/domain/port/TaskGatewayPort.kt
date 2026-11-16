package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.ImplementationStage
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus

/**
 * Port for Gateway A read endpoints: `GET /plans`, `GET /plans/{id}/status`, `GET /tasks/next`.
 */
interface TaskGatewayPort {
    suspend fun fetchPlans(): Result<List<PlanSummary>>
    suspend fun fetchPlanStatus(planId: String): Result<PlanStatusSnapshot>
    suspend fun fetchNextTasks(): Result<List<ManualTask>>
    suspend fun fetchStages(planId: String): Result<List<ImplementationStage>>
}

data class PlanSummary(
    val id: String,
    val title: String,
    val taskCount: Int,
    val completedTaskCount: Int,
    val status: String,
)

data class PlanStatusSnapshot(
    val planId: String,
    val status: String,
    val halted: Boolean,
    val openBranches: List<String>,
    val blockedTasks: List<String>,
    val currentMilestone: String,
)
