package com.inscopelabs.buildengine.data.remote

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.BranchHeadDto
import com.inscopelabs.buildengine.core.network.dto.DoctorReportDto
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.core.network.dto.EngineScopeRequestDto
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.GatewayHealthDto
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
import com.inscopelabs.buildengine.core.result.AppResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Talks to the gateway. The only component in the app that speaks HTTP.
 *
 * Design review §8: every request produces an audit record on the gateway side, and
 * nothing in this class may log a credential, a capability signature or a nonce.
 *
 * ### Where failures go
 *
 * [GatewayApi] has no failure channel — its methods return the DTO or they throw —
 * so a refusal arrives here as a [GatewayHttpException] and propagates. The layer
 * above (`data.repository`) catches it and maps it with [ErrorMapper] into the
 * `AppResult` the domain sees. Mapping it here and throwing the result would only
 * move the same information one frame up.
 *
 * [health] is the exception: it returns an `AppResult` directly, because the
 * gateway preflight is a question with a yes/no answer that the operator asks
 * before anything else.
 */
class GatewayRepository(
    private val httpClient: GatewayHttpClient,
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : GatewayApi {

    override suspend fun listPlans(): List<PlanSummaryDto> =
        decode(httpClient.get(GatewayEndpoints.plans()))

    override suspend fun getPlan(planId: String): PlanSummaryDto =
        listPlans().firstOrNull { it.id == planId }
            ?: throw GatewayProtocolException("the gateway has no plan '$planId'")

    override suspend fun planStatus(planId: String): PlanStatusDto =
        decode(httpClient.get(GatewayEndpoints.planStatus(planId)))

    override suspend fun validatePlan(planId: String): PlanValidationDto =
        decode(httpClient.post(GatewayEndpoints.planValidate(planId), "{}"))

    override suspend fun renderCheck(planId: String): RenderCheckDto =
        decode(httpClient.get(GatewayEndpoints.planRenderCheck(planId)))

    override suspend fun nextTasks(): List<TaskSummaryDto> =
        decode(httpClient.get(GatewayEndpoints.tasksNext()))

    override suspend fun branchHead(branch: String): BranchHeadDto =
        decode(httpClient.get(GatewayEndpoints.branchHead(branch)))

    override suspend fun publish(request: PublishRequestDto): GatewayAckDto =
        decode(
            httpClient.post(
                path = GatewayEndpoints.publish(request.task),
                body = json.encodeToString(request),
            )
        )

    override suspend fun reconcile(request: ReconcileRequestDto): GatewayAckDto =
        decode(
            httpClient.post(
                path = GatewayEndpoints.reconcile(request.task),
                body = json.encodeToString(request),
            )
        )

    override suspend fun upload(request: UploadRequestDto): GatewayAckDto =
        decode(
            httpClient.post(
                path = GatewayEndpoints.upload(request.task),
                body = json.encodeToString(request),
            )
        )

    override suspend fun halt(request: EngineScopeRequestDto): GatewayAckDto =
        decode(httpClient.post(GatewayEndpoints.halt(), json.encodeToString(request)))

    override suspend fun resume(request: EngineScopeRequestDto): GatewayAckDto =
        decode(httpClient.post(GatewayEndpoints.resume(), json.encodeToString(request)))

    override suspend fun sync(request: SyncRequestDto): SyncStatusDto =
        decode(httpClient.post(GatewayEndpoints.sync(), json.encodeToString(request)))

    override suspend fun doctor(): DoctorReportDto =
        decode(httpClient.get(GatewayEndpoints.doctor()))

    override suspend fun enroll(request: EnrollRequestDto): EnrollResponseDto =
        decode(httpClient.post(GatewayEndpoints.enroll(), json.encodeToString(request)))

    override suspend fun rotateKey(request: RotateKeyRequestDto): EnrollResponseDto =
        decode(httpClient.post(GatewayEndpoints.enrollRotate(), json.encodeToString(request)))

    /**
     * The gateway preflight — `GET /health`.
     *
     * It is the one call the console can make with no write capability at all, so
     * it is also the one that must work when everything else is refused.
     *
     * The answer carries the gateway's `environment`, and that is not decoration:
     * the Lab refuses to run against anything but a `"test"` gateway
     * (docs/gateway-contract.md §3.1), so the value has to reach the caller
     * rather than being collapsed into a boolean here.
     */
    suspend fun health(): AppResult<GatewayHealthDto> = try {
        AppResult.Success(decode(httpClient.get(GatewayEndpoints.health())))
    } catch (error: GatewayHttpException) {
        errorMapper.map(error.statusCode, error.body)
    } catch (error: GatewayProtocolException) {
        errorMapper.mapProtocolFailure(error.message ?: "the gateway's answer could not be read")
    }

    /**
     * Reads the gateway's answer.
     *
     * A body that is not the documented shape is not a refusal: the gateway took the
     * request and then answered in a way the console cannot read. It gets its own
     * failure, because "the gateway said no" and "the gateway said something else"
     * call for different operator reactions.
     */
    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString(body)
    } catch (error: SerializationException) {
        throw GatewayProtocolException(
            "the gateway's answer could not be read: ${error.message}"
        )
    }

    private companion object {
        /**
         * Unknown fields are ignored — the contract requires it (§1.5), so that a
         * gateway that adds one does not break an older console. A *missing* field
         * is a different matter: the DTOs carry defaults, so a missing field reads
         * as its default rather than as an error. The gateway is expected to send
         * the documented fields in full.
         */
        val json = Json { ignoreUnknownKeys = true }
    }
}
