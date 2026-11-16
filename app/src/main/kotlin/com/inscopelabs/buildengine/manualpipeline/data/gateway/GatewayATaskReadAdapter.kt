package com.inscopelabs.buildengine.manualpipeline.data.gateway

import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.StatusMapper
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.TaskReadMapper
import com.inscopelabs.buildengine.manualpipeline.domain.model.ImplementationStage
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanStatusSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanSummary
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import javax.inject.Inject

/**
 * Gateway A read adapter for `/plans`, `/plans/{id}/status`, and `/tasks/next`.
 */
class GatewayATaskReadAdapter @Inject constructor(
    private val gatewayApi: GatewayApi,
) : TaskGatewayPort {

    override suspend fun fetchPlans(): Result<List<PlanSummary>> = runCatching {
        gatewayApi.listPlans().map(TaskReadMapper::toPlanSummary)
    }

    override suspend fun fetchPlanStatus(planId: String): Result<PlanStatusSnapshot> = runCatching {
        StatusMapper.toSnapshot(gatewayApi.planStatus(planId))
    }

    override suspend fun fetchNextTasks(): Result<List<ManualTask>> = runCatching {
        gatewayApi.nextTasks().map(TaskReadMapper::toDomain)
    }

    override suspend fun fetchStages(planId: String): Result<List<ImplementationStage>> = runCatching {
        val status = gatewayApi.planStatus(planId)
        val milestone = status.currentMilestone.trim()
        buildList {
            if (milestone.isNotEmpty()) {
                add(
                    ImplementationStage(
                        id = milestone,
                        title = "Active Milestone: $milestone",
                        orderIndex = 0,
                        milestone = milestone,
                        summary = "Gateway plan status: ${status.status}",
                    ),
                )
            }
        }
    }
}
