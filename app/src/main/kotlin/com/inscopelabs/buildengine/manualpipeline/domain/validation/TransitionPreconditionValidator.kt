package com.inscopelabs.buildengine.manualpipeline.domain.validation

import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import javax.inject.Inject

/**
 * Advisory client-side precondition validator for `POST /plans/{id}/status` transitions.
 * Gateway A remains strictly authoritative on write (I-02).
 */
class TransitionPreconditionValidator @Inject constructor() {

    data class Result(
        val permitted: Boolean,
        val reason: String,
    )

    fun validate(task: ManualTask, kind: TransitionKind): Result {
        if (!task.hasOrderId) {
            return Result(
                permitted = false,
                reason = "Work-order identifier is missing; console never invents order IDs.",
            )
        }
        if (!task.hasValidHeadSha) {
            return Result(
                permitted = false,
                reason = "Task head SHA must be a full 40-character hex commit.",
            )
        }
        if (task.branch.isBlank()) {
            return Result(
                permitted = false,
                reason = "Task branch name is missing.",
            )
        }
        if (task.isPlanHalted) {
            return Result(
                permitted = false,
                reason = "Plan is currently halted; writes are blocked until resumed.",
            )
        }
        if (task.status == TaskStatus.UNKNOWN) {
            return Result(
                permitted = false,
                reason = "Task status '${task.status.wireValue}' is unrecognized; failing closed.",
            )
        }
        if (task.status == TaskStatus.BLOCKED) {
            return Result(
                permitted = false,
                reason = "Task is marked blocked by the gateway.",
            )
        }

        return when (kind) {
            TransitionKind.SET_IN_PROGRESS -> when (task.status) {
                TaskStatus.READY -> Result(true, "Ready to transition from ready -> in_progress.")
                TaskStatus.IN_PROGRESS -> Result(false, "Task is already in_progress.")
                TaskStatus.COMPLETED -> Result(false, "Completed tasks cannot move back to in_progress.")
                else -> Result(false, "Transition to in_progress requires status ready (currently ${task.status.wireValue}).")
            }
            TransitionKind.SET -> when (task.status) {
                TaskStatus.READY, TaskStatus.IN_PROGRESS -> Result(true, "Eligible for stage status update.")
                else -> Result(false, "Stage status update requires ready or in_progress (currently ${task.status.wireValue}).")
            }
            TransitionKind.SET_DONE -> when (task.status) {
                TaskStatus.IN_PROGRESS -> Result(true, "Ready to complete from in_progress -> completed.")
                TaskStatus.READY -> Result(true, "Task is ready; Gateway A guards verify completion readiness.")
                TaskStatus.COMPLETED -> Result(false, "Task is already completed.")
                else -> Result(false, "Completion requires active task status (currently ${task.status.wireValue}).")
            }
        }
    }
}
