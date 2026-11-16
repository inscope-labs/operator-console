package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Permitted status transition capability kinds in the manual pipeline (contract §3.17).
 */
enum class TransitionKind(val wireTargetStatus: String, val displayLabel: String) {
    SET_IN_PROGRESS("in_progress", "Start Task (in_progress)"),
    SET("in_progress", "Set Stage Status"),
    SET_DONE("completed", "Complete Task (completed)");
}
