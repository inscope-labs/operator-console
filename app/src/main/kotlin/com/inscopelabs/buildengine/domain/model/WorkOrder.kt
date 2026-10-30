package com.inscopelabs.buildengine.domain.model

/**
 * A work order.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: an order is scoped to the selected tasks only.
 */
data class WorkOrder(
    val id: String,
    val planId: String,
    val taskIds: List<String>,
)
