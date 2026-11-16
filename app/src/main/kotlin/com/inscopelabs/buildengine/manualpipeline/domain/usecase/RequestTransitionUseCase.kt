package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintingPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.StatusTransitionPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HaltStateValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.TransitionPreconditionValidator
import javax.inject.Inject

/**
 * MP-003: Executes a single confirmed status transition (`POST /plans/{id}/status`) routed
 * through [MintingPort] (single mint gate) and [StatusTransitionPort] (existing journaled
 * executor). Never mints capabilities locally and never sends without operator confirmation.
 */
class RequestTransitionUseCase @Inject constructor(
    private val resolveAuthorityUseCase: ResolveAuthorityUseCase,
    private val transitionPreconditionValidator: TransitionPreconditionValidator,
    private val haltStateValidator: HaltStateValidator,
    private val headFreshnessValidator: HeadFreshnessValidator,
    private val taskGatewayPort: TaskGatewayPort,
    private val branchHeadPort: BranchHeadPort,
    private val mintingPort: MintingPort,
    private val statusTransitionPort: StatusTransitionPort,
    private val recordWorkflowMetadataUseCase: RecordWorkflowMetadataUseCase,
    private val recordOperatorActionUseCase: RecordOperatorActionUseCase,
) {

    sealed interface TransitionResult {
        data class Executed(val outcome: OperationOutcome, val verifiedLiveHeadSha: String) : TransitionResult
        data class MintBlocked(val reason: String, val code: String, val liveHeadSha: String? = null) : TransitionResult
    }

    suspend operator fun invoke(
        task: ManualTask,
        request: TransitionRequest,
    ): TransitionResult {
        val opName = request.kind.name
        val authority = resolveAuthorityUseCase(opName)
        if (!authority.canAct) {
            return TransitionResult.MintBlocked(
                reason = authority.blockingReason ?: "Operator authority unavailable.",
                code = if (!authority.environmentValidation.matched) "ENVIRONMENT_MISMATCH" else "AUTH",
            )
        }

        val precondition = transitionPreconditionValidator.validate(task, request.kind)
        if (!precondition.permitted) {
            return TransitionResult.MintBlocked(
                reason = precondition.reason,
                code = "POLICY_REFUSED",
            )
        }

        val planStatus = taskGatewayPort.fetchPlanStatus(request.planId).getOrNull()
        val haltCheck = haltStateValidator.validate(planStatus)
        if (haltCheck.halted) {
            return TransitionResult.MintBlocked(
                reason = haltCheck.reason,
                code = "HALT_ACTIVE",
            )
        }

        val liveBranch = branchHeadPort.fetchLiveHead(request.branch).getOrElse { err ->
            return TransitionResult.MintBlocked(
                reason = "Cannot verify live branch head: ${err.message ?: "unavailable"}",
                code = "HEAD_UNAVAILABLE",
            )
        }
        when (val freshness = headFreshnessValidator.validate(request.expectedHead, liveBranch.headSha)) {
            is HeadFreshnessValidator.Result.Drifted -> return TransitionResult.MintBlocked(
                reason = "Branch head drifted since inspection (${freshness.expectedSha.take(7)} -> ${freshness.liveSha.take(7)}).",
                code = "HEAD_DRIFT",
                liveHeadSha = freshness.liveSha,
            )
            is HeadFreshnessValidator.Result.InvalidFormat -> return TransitionResult.MintBlocked(
                reason = "Head SHA must be a full 40-char commit hash.",
                code = "BRANCH_INVALID",
            )
            is HeadFreshnessValidator.Result.Fresh -> Unit
        }

        val mintedHandle = when (val mint = mintingPort.mintForTransition(request)) {
            is MintOutcome.Minted -> mint.handle
            is MintOutcome.HeadDrifted -> return TransitionResult.MintBlocked(
                reason = "Branch head drifted while confirming (${mint.liveSha.take(7)}).",
                code = "HEAD_DRIFT",
                liveHeadSha = mint.liveSha,
            )
            is MintOutcome.HeadUnavailable -> return TransitionResult.MintBlocked(
                reason = "Branch head verification unavailable: ${mint.reason}",
                code = "HEAD_UNAVAILABLE",
            )
            is MintOutcome.Declined -> return TransitionResult.MintBlocked(
                reason = "Operator declined confirmation. Nothing was sent.",
                code = "DECLINED",
            )
            is MintOutcome.AuthFailed -> return TransitionResult.MintBlocked(
                reason = "Authentication failed: ${mint.reason}",
                code = "AUTH",
            )
            is MintOutcome.LockedOut -> return TransitionResult.MintBlocked(
                reason = "Biometric authentication is locked out.",
                code = "LOCKED_OUT",
            )
            is MintOutcome.KeyInvalidated -> return TransitionResult.MintBlocked(
                reason = "Device signing key is invalidated; re-enrol device key.",
                code = "KEY_INVALIDATED",
            )
            is MintOutcome.NotEnrolled -> return TransitionResult.MintBlocked(
                reason = "Device key is not enrolled.",
                code = "NOT_ENROLLED",
            )
            is MintOutcome.ExpiredWhileConfirming -> return TransitionResult.MintBlocked(
                reason = "Confirmation expired before mint completed.",
                code = "EXPIRED",
            )
            is MintOutcome.TierNotAllowed -> return TransitionResult.MintBlocked(
                reason = mint.reason,
                code = "AUTH_TIER_NOT_ALLOWED",
            )
            is MintOutcome.EnvironmentMismatch -> return TransitionResult.MintBlocked(
                reason = "Environment mismatch: expected ${mint.expected}, gateway reported ${mint.reported}.",
                code = "ENVIRONMENT_MISMATCH",
            )
        }

        val outcome = statusTransitionPort.submitTransition(request, mintedHandle)
        recordMetadataAndHistory(task, request, outcome)
        return TransitionResult.Executed(outcome = outcome, verifiedLiveHeadSha = liveBranch.headSha)
    }

    private suspend fun recordMetadataAndHistory(
        task: ManualTask,
        request: TransitionRequest,
        outcome: OperationOutcome,
    ) {
        val (label, key, auditRef, headSha) = when (outcome) {
            is OperationOutcome.Succeeded -> Quad("SUCCEEDED", null, outcome.auditRef, outcome.headSha)
            is OperationOutcome.Refused -> Quad("REFUSED:${outcome.code}", null, outcome.auditRef, request.expectedHead)
            is OperationOutcome.Conflict -> Quad("CONFLICT:${outcome.code}", null, outcome.auditRef, outcome.liveHeadSha ?: request.expectedHead)
            is OperationOutcome.Timeout -> Quad("TIMEOUT", outcome.idempotencyKey, null, request.expectedHead)
            is OperationOutcome.UncertainCompletion -> Quad("UNCERTAIN", outcome.idempotencyKey, outcome.auditRef, outcome.headSha ?: request.expectedHead)
            is OperationOutcome.RecoveryRequired -> Quad("RECOVERY_REQUIRED", outcome.idempotencyKey, outcome.auditRef, request.expectedHead)
        }

        recordWorkflowMetadataUseCase(
            taskId = task.id,
            planId = task.planId,
            orderId = task.orderId,
            branch = request.branch,
            headSha = headSha,
            stageId = task.stage?.id ?: "",
            statusWire = if (outcome is OperationOutcome.Succeeded) request.targetStatus.wireValue else task.status.wireValue,
            phaseLabel = "TRANSITION_${request.kind.name}_$label",
            idempotencyKey = key,
            auditRef = auditRef,
        )

        recordOperatorActionUseCase(
            taskId = task.id,
            planId = task.planId,
            actionName = request.kind.name,
            outcomeLabel = label,
            detail = "Transition ${request.currentStatus.wireValue} -> ${request.targetStatus.wireValue} ($label)",
            idempotencyKey = key,
            auditRef = auditRef,
            headSha = headSha,
        )
    }

    private data class Quad(
        val label: String,
        val key: String?,
        val auditRef: String?,
        val headSha: String,
    )
}
