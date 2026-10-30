package com.inscopelabs.buildengine.data.remote

import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto

/**
 * The gateway's read and write surface, as the app consumes it.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: the read-only commands are half the operator surface. Serving
 * them without a write capability is phase 1 of the recommended sequence.
 */
interface GatewayApi {

    suspend fun listPlans(): List<PlanSummaryDto>

    suspend fun getPlan(planId: String): PlanSummaryDto

    suspend fun nextTasks(): List<TaskSummaryDto>

    suspend fun publish(request: PublishRequestDto): GatewayAckDto
}
