package com.inscopelabs.buildengine.domain.model

/**
 * Read representation of a scoped work order identifier referenced by capabilities.
 * Work-order creation is deferred to engine tooling under BD-1.3 Option B (PR3-027).
 */
data class WorkOrder(
    val id: String,
    val planId: String,
    val taskIds: List<String>,
)
