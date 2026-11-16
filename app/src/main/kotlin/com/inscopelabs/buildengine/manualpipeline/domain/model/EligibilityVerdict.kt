package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Local advisory verdict on whether a task appears eligible for manual action.
 * Never authoritative — Gateway A enforces all guards and preconditions on write.
 */
data class EligibilityVerdict(
    val taskId: String,
    val advisoryReady: Boolean,
    val checks: List<EligibilityCheck>,
    val summaryReason: String,
) {
    val isAdvisoryOnly: Boolean = true
}
