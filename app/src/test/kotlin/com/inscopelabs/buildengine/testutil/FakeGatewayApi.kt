package com.inscopelabs.buildengine.testutil

import com.inscopelabs.buildengine.core.network.dto.BranchHeadDto
import com.inscopelabs.buildengine.core.network.dto.DecideAckDto
import com.inscopelabs.buildengine.core.network.dto.DecideRequestDto
import com.inscopelabs.buildengine.core.network.dto.DoctorReportDto
import com.inscopelabs.buildengine.core.network.dto.EnrollChallengeDto
import com.inscopelabs.buildengine.core.network.dto.EngineScopeRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollRequestDto
import com.inscopelabs.buildengine.core.network.dto.EnrollResponseDto
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.OperationLookupDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionAckDto
import com.inscopelabs.buildengine.core.network.dto.PlanStatusTransitionRequestDto
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
import com.inscopelabs.buildengine.data.remote.GatewayApi

class FakeGatewayApi : GatewayApi {

    var enrollResult: (EnrollRequestDto) -> EnrollResponseDto = {
        EnrollResponseDto(kid = "k1", status = "active", enrolledAt = 1700000000L)
    }

    var rotateKeyResult: (RotateKeyRequestDto) -> EnrollResponseDto = {
        EnrollResponseDto(kid = "k2", status = "active", enrolledAt = 1700000100L)
    }

    var enrollChallengeResult: () -> EnrollChallengeDto = {
        EnrollChallengeDto(
            challenge = "dGVzdC1jaGFsbGVuZ2UtMzItYnl0ZXMtc3RyaW5nLTAwMQ",
            expiresAt = 1700000300L,
        )
    }

    val transitionPlanStatusCalls = mutableListOf<PlanStatusTransitionRequestDto>()
    var transitionPlanStatusResult: (PlanStatusTransitionRequestDto) -> PlanStatusTransitionAckDto = { request ->
        PlanStatusTransitionAckDto(
            accepted = true,
            operation = "SET_IN_PROGRESS",
            plan = request.plan,
            task = request.task,
            newStatus = request.targetStatus,
            headSha = request.expectedHead,
            auditRef = "ref-status-1",
        )
    }

    val decideCalls = mutableListOf<DecideRequestDto>()
    var decideResult: (DecideRequestDto) -> DecideAckDto = { request ->
        DecideAckDto(
            accepted = true,
            operation = "DECIDE",
            plan = request.plan,
            decision = request.decision,
            auditRef = "ref-decide-1",
        )
    }

    val uploadCalls = mutableListOf<UploadRequestDto>()
    var uploadResult: (UploadRequestDto) -> GatewayAckDto = {
        GatewayAckDto(accepted = true, operation = "upload", auditRef = "ref-3")
    }

    var reconcileResult: (ReconcileRequestDto) -> GatewayAckDto = {
        GatewayAckDto(accepted = true, operation = "reconcile", auditRef = "ref-2")
    }

    var haltResult: (EngineScopeRequestDto) -> GatewayAckDto = {
        GatewayAckDto(accepted = true, operation = "halt", auditRef = "ref-4")
    }

    var resumeResult: (EngineScopeRequestDto) -> GatewayAckDto = {
        GatewayAckDto(accepted = true, operation = "resume", auditRef = "ref-5")
    }

    override suspend fun listPlans(): List<PlanSummaryDto> = emptyList()
    override suspend fun getPlan(planId: String): PlanSummaryDto = PlanSummaryDto(id = planId, title = "Plan", status = "active")
    override suspend fun planStatus(planId: String): PlanStatusDto = PlanStatusDto(id = planId, status = "active")
    override suspend fun transitionPlanStatus(request: PlanStatusTransitionRequestDto): PlanStatusTransitionAckDto {
        transitionPlanStatusCalls += request
        return transitionPlanStatusResult(request)
    }
    override suspend fun decide(request: DecideRequestDto): DecideAckDto {
        decideCalls += request
        return decideResult(request)
    }
    override suspend fun validatePlan(planId: String): PlanValidationDto = PlanValidationDto(valid = true, planId = planId)
    override suspend fun renderCheck(planId: String): RenderCheckDto = RenderCheckDto(ok = true, planId = planId)
    override suspend fun nextTasks(): List<TaskSummaryDto> = emptyList()
    /** The live head [branchHead] reports for any branch. */
    var branchHeadResult: (String) -> String = { "0".repeat(40) }

    override suspend fun branchHead(branch: String): BranchHeadDto = BranchHeadDto(branch = branch, headSha = branchHeadResult(branch))
    override suspend fun publish(request: PublishRequestDto): GatewayAckDto = GatewayAckDto(accepted = true, operation = "publish", auditRef = "ref-1")
    override suspend fun reconcile(request: ReconcileRequestDto): GatewayAckDto = reconcileResult(request)
    override suspend fun upload(request: UploadRequestDto): GatewayAckDto {
        uploadCalls += request
        return uploadResult(request)
    }
    override suspend fun halt(request: EngineScopeRequestDto): GatewayAckDto = haltResult(request)
    override suspend fun resume(request: EngineScopeRequestDto): GatewayAckDto = resumeResult(request)
    override suspend fun sync(request: SyncRequestDto): SyncStatusDto = SyncStatusDto(status = "synced", headSha = "0".repeat(40), inSync = true)
    override suspend fun doctor(): DoctorReportDto = DoctorReportDto(healthy = true, checks = emptyList())
    var lookupResult: (String) -> OperationLookupDto = { key ->
        OperationLookupDto(
            id = key,
            operation = "PUBLISH",
            status = "completed",
            branch = "task-10008-refactor-parser",
            headSha = "0".repeat(40),
            auditRef = "audit-default",
            timestamp = 1761840000L,
        )
    }

    override suspend fun enroll(request: EnrollRequestDto): EnrollResponseDto = enrollResult(request)
    override suspend fun rotateKey(request: RotateKeyRequestDto): EnrollResponseDto = rotateKeyResult(request)
    override suspend fun enrollChallenge(): EnrollChallengeDto = enrollChallengeResult()
    override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto = lookupResult(idempotencyKey)
}
