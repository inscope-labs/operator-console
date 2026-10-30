package com.inscopelabs.buildengine.data.remote

import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.core.result.AppResult

/**
 * Talks to the gateway. The only component in the app that speaks HTTP.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §8: every request produces an audit record on the gateway side, and
 * nothing in this class may log a credential, a capability signature or a nonce.
 */
class GatewayRepository(
    private val httpClient: GatewayHttpClient,
) : GatewayApi {

    override suspend fun listPlans(): List<PlanSummaryDto> =
        TODO("SCAFFOLD: GET the plan list from the gateway.")

    override suspend fun getPlan(planId: String): PlanSummaryDto =
        TODO("SCAFFOLD: GET one plan from the gateway.")

    override suspend fun nextTasks(): List<TaskSummaryDto> =
        TODO("SCAFFOLD: GET the next actionable tasks from the gateway.")

    override suspend fun publish(request: PublishRequestDto): GatewayAckDto =
        TODO("SCAFFOLD: POST the publish request with its capability.")

    suspend fun health(): AppResult<Unit> =
        TODO("SCAFFOLD: the gateway preflight (design review §12, doctor).")
}
