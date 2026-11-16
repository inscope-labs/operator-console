package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Interpreted work order for a manual pipeline task.
 * Work-order creation remains deferred to engine tooling (ADR 0014 D5);
 * the console reads gateway-reported identifiers and declared deliverables only.
 */
data class WorkOrder(
    val orderId: String,
    val planId: String,
    val taskId: String,
    val summary: String,
    val deliverables: List<Deliverable>,
    val companionFiles: List<CompanionFile>,
    val acceptanceCriteria: List<AcceptanceCriterion>,
)
