package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A task as the gateway reports it.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@Serializable
data class TaskSummaryDto(
    val id: String = "",
    val planId: String = "",
    val title: String = "",
    val status: String = "PENDING",
    val branch: String = "",
    val headSha: String = "",
)
