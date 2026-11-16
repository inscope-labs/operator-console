package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.domain.model.ImplementationStage
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadInfo
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanStatusSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanSummary
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates Gateway A task discovery, plan status reads, and live branch head verification.
 */
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskGatewayPort: TaskGatewayPort,
    private val branchHeadPort: BranchHeadPort,
) {
    suspend fun getPlans(): Result<List<PlanSummary>> = taskGatewayPort.fetchPlans()
    suspend fun getPlanStatus(planId: String): Result<PlanStatusSnapshot> = taskGatewayPort.fetchPlanStatus(planId)
    suspend fun getNextTasks(): Result<List<ManualTask>> = taskGatewayPort.fetchNextTasks()
    suspend fun getStages(planId: String): Result<List<ImplementationStage>> = taskGatewayPort.fetchStages(planId)
    suspend fun getLiveHead(branch: String): Result<BranchHeadInfo> = branchHeadPort.fetchLiveHead(branch)
}
