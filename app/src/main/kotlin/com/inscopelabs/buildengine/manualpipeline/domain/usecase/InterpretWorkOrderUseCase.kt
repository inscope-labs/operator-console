package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.AcceptanceCriterion
import com.inscopelabs.buildengine.manualpipeline.domain.model.CompanionFile
import com.inscopelabs.buildengine.manualpipeline.domain.model.Deliverable
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.WorkOrder
import javax.inject.Inject

/**
 * MP-004: Interprets gateway-reported task/work-order metadata into structured deliverables,
 * companion files, and acceptance criteria. Work-order creation stays deferred (ADR 0014 D5):
 * this use case never invents an `orderId` when the gateway provides none.
 */
class InterpretWorkOrderUseCase @Inject constructor() {

    operator fun invoke(
        task: ManualTask,
        extraDeclaredDeliverables: List<Deliverable> = emptyList(),
        companionFiles: List<CompanionFile> = emptyList(),
        acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    ): WorkOrder {
        val defaultDeliverables = if (extraDeclaredDeliverables.isNotEmpty()) {
            extraDeclaredDeliverables
        } else {
            listOf(
                Deliverable(
                    relativePath = "submission/${task.id}/manifest.json",
                    description = "Task submission metadata manifest",
                    required = false,
                ),
            )
        }

        val defaultCriteria = if (acceptanceCriteria.isNotEmpty()) {
            acceptanceCriteria
        } else {
            listOf(
                AcceptanceCriterion(
                    id = "AC-HEAD",
                    description = "Target branch head SHA verified at 40 hex characters before minting.",
                ),
                AcceptanceCriterion(
                    id = "AC-ZIP",
                    description = "Single immutable submission .zip passes contract §3.10 path & size rules.",
                ),
            )
        }

        val summary = if (task.hasOrderId) {
            "Work order ${task.orderId} for task '${task.title}' on branch ${task.branch}."
        } else {
            "Gateway reported no work-order identifier for task '${task.id}'. Privileged writes remain disabled."
        }

        return WorkOrder(
            orderId = task.orderId,
            planId = task.planId,
            taskId = task.id,
            summary = summary,
            deliverables = defaultDeliverables,
            companionFiles = companionFiles,
            acceptanceCriteria = defaultCriteria,
        )
    }
}
