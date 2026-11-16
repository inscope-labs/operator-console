package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import javax.inject.Inject

/**
 * MP-001: Discovers next eligible tasks from Gateway A (`GET /tasks/next`), enriches with
 * plan halt/blocked status, display-only `stages.json` stage metadata, and live branch head SHA.
 */
class DiscoverEligibleTasksUseCase @Inject constructor(
    private val taskGatewayPort: TaskGatewayPort,
    private val branchHeadPort: BranchHeadPort,
) {

    data class DiscoveryResult(
        val tasks: List<ManualTask>,
        val anyPlanHalted: Boolean,
        val activePlanId: String?,
        val warning: String? = null,
    )

    suspend operator fun invoke(): Result<DiscoveryResult> {
        val nextTasks = taskGatewayPort.fetchNextTasks().getOrElse { error ->
            return Result.failure(error)
        }
        val plans = taskGatewayPort.fetchPlans().getOrDefault(emptyList())
        val planIds = (nextTasks.map { it.planId } + plans.map { it.id })
            .filter { it.isNotBlank() }
            .distinct()

        val planStatuses = planIds.associateWith { planId ->
            taskGatewayPort.fetchPlanStatus(planId).getOrNull()
        }
        val planStages = planIds.associateWith { planId ->
            taskGatewayPort.fetchStages(planId).getOrDefault(emptyList())
        }

        val enriched = nextTasks.map { task ->
            val pStatus = planStatuses[task.planId]
            val stages = planStages[task.planId].orEmpty()
            val liveHead = if (task.branch.isNotBlank()) {
                branchHeadPort.fetchLiveHead(task.branch).getOrNull()?.headSha
                    ?.takeIf { it.isNotBlank() } ?: task.headSha
            } else {
                task.headSha
            }
            val matchedStage = task.stage ?: stages.firstOrNull { stage ->
                pStatus?.currentMilestone?.isNotBlank() == true &&
                    (stage.milestone == pStatus.currentMilestone || stage.id == pStatus.currentMilestone)
            } ?: stages.firstOrNull()

            task.copy(
                headSha = liveHead,
                stage = matchedStage,
                isPlanHalted = pStatus?.halted == true,
                isBlockedByGateway = pStatus?.blockedTasks?.contains(task.id) == true,
            )
        }

        val anyHalted = planStatuses.values.any { it?.halted == true }
        val activePlanId = enriched.firstOrNull()?.planId ?: plans.firstOrNull()?.id

        return Result.success(
            DiscoveryResult(
                tasks = enriched,
                anyPlanHalted = anyHalted,
                activePlanId = activePlanId,
            ),
        )
    }
}
