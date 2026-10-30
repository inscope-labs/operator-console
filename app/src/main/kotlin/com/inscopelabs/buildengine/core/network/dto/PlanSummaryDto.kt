package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A plan as the gateway reports it.
 *
 * docs/gateway-contract.md §3.2 — `GET /plans`.
 *
 * The read half of the operator surface. A plan is the plan of record: the private
 * document that names the work, and the thing a capability is bound to.
 *
 * `status` and the counts are the gateway's vocabulary, not the domain's. They are
 * carried as they arrive; mapping them onto `domain.model` is `data.repository`'s
 * job and not this one's.
 */
@Serializable
data class PlanSummaryDto(
    val id: String = "",
    val title: String = "",
    val status: String = "",
    val taskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val updatedAt: Long = 0L,
)
