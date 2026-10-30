package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * A task as the gateway reports it.
 *
 * docs/gateway-contract.md §3.6 — `GET /tasks/next`.
 *
 * A task is a unit of work within a plan. The branch and the head SHA travel with
 * it because the operator's decision is about a *specific* branch at a *specific*
 * SHA (design review §5), and the console has to be able to show both before
 * anything is signed.
 *
 * `status` is the gateway's vocabulary — `"ready"`, not `"READY"` — and it is
 * carried as it arrives. A status the console does not recognise is a task it
 * cannot summarise, not a reason to guess.
 */
@Serializable
data class TaskSummaryDto(
    val id: String = "",
    val planId: String = "",
    val title: String = "",
    val branch: String = "",
    val status: String = "",
    val headSha: String = "",
)
