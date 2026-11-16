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
        if (task.isBlockedByGateway) {
            // The gateway lists the task in `blockedTasks` (contract §3.6 plan status). The
            // eligibility evaluator already refuses it, so the transition gate must agree:
            // otherwise the panel says "not eligible" while the action is still offered.
            return Result(
                permitted = false,
                reason = "Task is listed in the gateway's blockedTasks; writes are blocked until it is unblocked.",
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
        if (kind == TransitionKind.SET_DONE && SET_DONE_AWAITS_SEAL_ROUTE) {
            return Result(
                permitted = false,
                reason = "Completion (SET_DONE) is not dispatched: a console upload has no seal route yet " +
                    "(BE-MP-12), so the gateway would refuse it with SEAL_NOT_ATTESTED. " +
                    "The module fails closed and never records 'completed' locally.",
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

    companion object {
        /**
         * `SET_DONE` is held back until BE-MP-12 defines how a Gateway A upload produces a
         * Gateway C seal.
         *
         * The transition contract requires `SET_DONE` to be backed by a seal attestation for
         * the task and its work order, and the only console write path (`UPLOAD` through
         * Gateway A) produces no seal. Dispatching `SET_DONE` could therefore only ever be
         * refused with `SEAL_NOT_ATTESTED`, so the module refuses it locally and fails closed
         * (I-08) instead of spending a capability on a write the gateway cannot accept.
         *
         * Set this to `false` once BE-MP-12 is implemented and the operator has approved it.
         */
        internal const val SET_DONE_AWAITS_SEAL_ROUTE = true
    }
}
