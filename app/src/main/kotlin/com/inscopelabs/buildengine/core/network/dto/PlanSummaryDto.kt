package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A plan as the gateway reports it.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@Serializable
data class PlanSummaryDto(
    val id: String = "",
    val title: String = "",
    val taskCount: Int = 0,
)
