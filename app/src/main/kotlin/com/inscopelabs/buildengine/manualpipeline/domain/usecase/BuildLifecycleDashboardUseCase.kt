package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.ImplementationStage
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanStatusSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanSummary
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordSnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.port.WorkflowRecordStore
import javax.inject.Inject

/**
 * MP-015: Aggregates a clean lifecycle summary combining Gateway A plan status, discovered
 * tasks, display-only implementation stages, and module-local workflow metadata.
 */
class BuildLifecycleDashboardUseCase @Inject constructor(
    private val taskGatewayPort: TaskGatewayPort,
    private val workflowRecordStore: WorkflowRecordStore,
) {

    data class LifecycleDashboard(
        val plans: List<PlanSummary>,
        val planStatuses: Map<String, PlanStatusSnapshot>,
        val stagesByPlan: Map<String, List<ImplementationStage>>,
        val tasks: List<ManualTask>,
        val latestRecordByTask: Map<String, WorkflowRecordSnapshot?>,
        val readyCount: Int,
        val inProgressCount: Int,
        val completedCount: Int,
        val blockedCount: Int,
        val haltedPlanCount: Int,
    )

    suspend operator fun invoke(tasks: List<ManualTask>): LifecycleDashboard {
        val plans = taskGatewayPort.fetchPlans().getOrDefault(emptyList())
        val planIds = (plans.map { it.id } + tasks.map { it.planId }).filter { it.isNotBlank() }.distinct()
        val statuses = planIds.mapNotNull { id ->
            taskGatewayPort.fetchPlanStatus(id).getOrNull()?.let { id to it }
        }.toMap()
        val stages = planIds.associateWith { id ->
            taskGatewayPort.fetchStages(id).getOrDefault(emptyList())
        }
        val latestRecords = tasks.associate { task ->
            task.id to workflowRecordStore.latestForTask(task.id)
        }

        return LifecycleDashboard(
            plans = plans,
            planStatuses = statuses,
            stagesByPlan = stages,
            tasks = tasks,
            latestRecordByTask = latestRecords,
            readyCount = tasks.count { it.status == TaskStatus.READY },
            inProgressCount = tasks.count { it.status == TaskStatus.IN_PROGRESS },
            completedCount = tasks.count { it.status == TaskStatus.COMPLETED },
            blockedCount = tasks.count { it.status == TaskStatus.BLOCKED || it.isBlockedByGateway },
            haltedPlanCount = statuses.values.count { it.halted },
        )
    }
}
