package com.inscopelabs.buildengine.manualpipeline.domain.validation

import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanStatusSnapshot
import javax.inject.Inject

/**
 * Checks repository/plan halt state before presenting or confirming write actions.
 */
class HaltStateValidator @Inject constructor() {

    data class Result(
        val halted: Boolean,
        val reason: String,
    )

    fun validate(planStatus: PlanStatusSnapshot?): Result {
        if (planStatus == null) {
            return Result(
                halted = false,
                reason = "Plan halt status has not been loaded yet.",
            )
        }
        return if (planStatus.halted) {
            Result(
                halted = true,
                reason = "Repository halt active on plan '${planStatus.planId}'. All write actions are locked.",
            )
        } else {
            Result(
                halted = false,
                reason = "Engine active (no halt marker).",
            )
        }
    }
}
