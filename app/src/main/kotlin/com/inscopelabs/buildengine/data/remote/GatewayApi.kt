package com.inscopelabs.buildengine.data.remote

import com.inscopelabs.buildengine.core.network.dto.BranchHeadDto
import com.inscopelabs.buildengine.core.network.dto.DoctorReportDto
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.core.network.dto.EngineScopeRequestDto
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusDto
import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.PlanValidationDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.network.dto.ReconcileRequestDto
import com.inscopelabs.buildengine.core.network.dto.RenderCheckDto
import com.inscopelabs.buildengine.core.network.dto.RotateKeyRequestDto
import com.inscopelabs.buildengine.core.network.dto.SyncRequestDto
import com.inscopelabs.buildengine.core.network.dto.SyncStatusDto
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.core.network.dto.UploadRequestDto

/**
 * The gateway's read and write surface, as the app consumes it.
 *
 * Design review §12: the read-only commands are half the operator surface. Serving
 * them without a write capability is phase 1 of the recommended sequence.
 *
 * One method per endpoint of docs/gateway-contract.md §3.
 *
 * The interface has no failure channel: each method returns the gateway's answer or
 * throws. `data.repository` is where a refusal becomes an `AppResult`, because that
 * is the layer that knows what the domain wants to see.
 */
interface GatewayApi {

    /** `GET /plans` — the plan list. */
    suspend fun listPlans(): List<PlanSummaryDto>

    /**
     * One plan's summary.
     *
     * The gateway contract exposes no single-plan endpoint — §3.2 lists the plans
     * and §3.3 gives a plan's *status* — so this reads the plan collection and
     * finds the plan in it.
     */
    suspend fun getPlan(planId: String): PlanSummaryDto

    /** `GET /plans/{id}/status` — the status of one plan. */
    suspend fun planStatus(planId: String): PlanStatusDto

    /** `POST /plans/{id}/validate` — read-only validation of a plan. */
    suspend fun validatePlan(planId: String): PlanValidationDto

    /** `GET /plans/{id}/render-check` — whether a plan's views are current. */
    suspend fun renderCheck(planId: String): RenderCheckDto

    /** `GET /tasks/next` — the next actionable tasks. */
    suspend fun nextTasks(): List<TaskSummaryDto>

    /** `GET /branches/{name}/head` — the current remote head of a branch. */
    suspend fun branchHead(branch: String): BranchHeadDto

    /** `POST /tasks/{id}/publish`. */
    suspend fun publish(request: PublishRequestDto): GatewayAckDto

    /** `POST /tasks/{id}/reconcile`. */
    suspend fun reconcile(request: ReconcileRequestDto): GatewayAckDto

    /** `POST /tasks/{id}/upload`. */
    suspend fun upload(request: UploadRequestDto): GatewayAckDto

    /** `POST /halt`. */
    suspend fun halt(request: EngineScopeRequestDto): GatewayAckDto

    /** `POST /resume`. */
    suspend fun resume(request: EngineScopeRequestDto): GatewayAckDto

    /** `POST /sync`. */
    suspend fun sync(request: SyncRequestDto): SyncStatusDto

    /** `GET /doctor`. */
    suspend fun doctor(): DoctorReportDto

    /** `POST /enroll`. */
    suspend fun enroll(request: EnrollRequestDto): EnrollResponseDto

    /** `POST /enroll/rotate`. */
    suspend fun rotateKey(request: RotateKeyRequestDto): EnrollResponseDto
}
