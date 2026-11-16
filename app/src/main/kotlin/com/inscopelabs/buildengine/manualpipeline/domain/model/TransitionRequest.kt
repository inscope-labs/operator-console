package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Request to execute a single guarded `POST /plans/{id}/status` transition.
 * Only one transition is permitted per operator confirmation (MP-003).
 */
data class TransitionRequest(
    val planId: String,
    val taskId: String,
    val orderId: String,
    val branch: String,
    val expectedHead: String,
    val kind: TransitionKind,
    val currentStatus: TaskStatus,
    val targetStatus: TaskStatus,
    val evidenceSummary: String? = null,
    val typedHeadConfirmation: String? = null,
)
