package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintingPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.UploadPort
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HaltStateValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.SealStateValidator
import javax.inject.Inject

/**
 * MP-008: Submits a single immutable submission `.zip` (`POST /tasks/{id}/upload`) behind
 * authority checks, live head verification, [MintingPort] single-mint gate, and [UploadPort]
 * (backed by the host encrypted journal executor). Never re-packs or mutates archive bytes.
 */
class SubmitUploadUseCase @Inject constructor(
    private val resolveAuthorityUseCase: ResolveAuthorityUseCase,
    private val taskGatewayPort: TaskGatewayPort,
    private val branchHeadPort: BranchHeadPort,
    private val haltStateValidator: HaltStateValidator,
    private val headFreshnessValidator: HeadFreshnessValidator,
    private val sealStateValidator: SealStateValidator,
    private val mintingPort: MintingPort,
    private val uploadPort: UploadPort,
    private val recordWorkflowMetadataUseCase: RecordWorkflowMetadataUseCase,
    private val recordOperatorActionUseCase: RecordOperatorActionUseCase,
) {

    sealed interface UploadResult {
        data class Executed(val outcome: OperationOutcome, val verifiedHeadSha: String) : UploadResult
        data class Blocked(val code: String, val reason: String, val liveHeadSha: String? = null) : UploadResult
    }

    suspend operator fun invoke(
        task: ManualTask,
        manifest: SubmissionManifest,
        zipBase64: String,
        typedHeadConfirmation: String? = null,
        alreadyUploadedSha256: String? = null,
    ): UploadResult {
        val authority = resolveAuthorityUseCase("UPLOAD")
        if (!authority.canAct) {
            return UploadResult.Blocked(
                code = if (!authority.environmentValidation.matched) "ENVIRONMENT_MISMATCH" else "AUTH",
                reason = authority.blockingReason ?: "Operator authority unavailable.",
            )
        }
        if (!task.hasOrderId) {
            return UploadResult.Blocked(
                code = "ORDER_MISSING",
                reason = "Work-order identifier is required before uploading a submission.",
            )
        }
        if (zipBase64.isBlank()) {
            return UploadResult.Blocked(
                code = "ARCHIVE_EMPTY",
                reason = "No immutable archive payload is loaded.",
            )
        }

        val sealCheck = sealStateValidator.check(
            existingSubmissionId = manifest.submissionId,
            alreadyUploadedSha256 = alreadyUploadedSha256,
            candidateSha256 = manifest.digest.zipSha256,
        )
        if (sealCheck.sealed) {
            return UploadResult.Blocked(
                code = "SUBMISSION_IMMUTABLE",
                reason = sealCheck.reason,
            )
        }

        val planStatus = taskGatewayPort.fetchPlanStatus(task.planId).getOrNull()
        val haltCheck = haltStateValidator.validate(planStatus)
        if (haltCheck.halted) {
            return UploadResult.Blocked(code = "HALT_ACTIVE", reason = haltCheck.reason)
        }

        val liveBranch = branchHeadPort.fetchLiveHead(task.branch).getOrElse { err ->
            return UploadResult.Blocked(
                code = "HEAD_UNAVAILABLE",
                reason = "Cannot verify live branch head: ${err.message ?: "unavailable"}",
            )
        }
        when (val freshness = headFreshnessValidator.validate(task.headSha, liveBranch.headSha)) {
            is HeadFreshnessValidator.Result.Drifted -> return UploadResult.Blocked(
                code = "HEAD_DRIFT",
                reason = "Branch head drifted (${freshness.expectedSha.take(7)} -> ${freshness.liveSha.take(7)}).",
                liveHeadSha = freshness.liveSha,
            )
            is HeadFreshnessValidator.Result.InvalidFormat -> return UploadResult.Blocked(
                code = "BRANCH_INVALID",
                reason = "Full 40-char head SHA is required.",
            )
            is HeadFreshnessValidator.Result.Fresh -> Unit
        }

        val mintedHandle = when (
            val mint = mintingPort.mintForUpload(
                planId = task.planId,
                taskId = task.id,
                orderId = task.orderId,
                branch = task.branch,
                headSha = liveBranch.headSha,
                submissionId = manifest.submissionId,
                zipSha256 = manifest.digest.zipSha256,
                typedHeadConfirmation = typedHeadConfirmation,
            )
        ) {
            is MintOutcome.Minted -> mint.handle
            is MintOutcome.HeadDrifted -> return UploadResult.Blocked("HEAD_DRIFT", "Branch head drifted (${mint.liveSha.take(7)}).", mint.liveSha)
            is MintOutcome.HeadUnavailable -> return UploadResult.Blocked("HEAD_UNAVAILABLE", mint.reason)
            is MintOutcome.Declined -> return UploadResult.Blocked("DECLINED", "Operator declined upload confirmation.")
            is MintOutcome.AuthFailed -> return UploadResult.Blocked("AUTH", mint.reason)
            is MintOutcome.LockedOut -> return UploadResult.Blocked("LOCKED_OUT", "Biometric authentication locked out.")
            is MintOutcome.KeyInvalidated -> return UploadResult.Blocked("KEY_INVALIDATED", "Device key invalidated.")
            is MintOutcome.NotEnrolled -> return UploadResult.Blocked("NOT_ENROLLED", "Device key not enrolled.")
            is MintOutcome.ExpiredWhileConfirming -> return UploadResult.Blocked("EXPIRED", "Confirmation expired.")
            is MintOutcome.TierNotAllowed -> return UploadResult.Blocked("AUTH_TIER_NOT_ALLOWED", mint.reason)
            is MintOutcome.EnvironmentMismatch -> return UploadResult.Blocked(
                "ENVIRONMENT_MISMATCH",
                "Environment mismatch (${mint.expected} vs ${mint.reported}).",
            )
        }

        val outcome = uploadPort.submitUpload(
            planId = task.planId,
            taskId = task.id,
            orderId = task.orderId,
            branch = task.branch,
            headSha = liveBranch.headSha,
            submissionId = manifest.submissionId,
            zipSha256 = manifest.digest.zipSha256,
            zipBase64 = zipBase64,
            mintedCapability = mintedHandle,
        )

        recordOutcome(task, manifest, liveBranch.headSha, outcome)
        return UploadResult.Executed(outcome = outcome, verifiedHeadSha = liveBranch.headSha)
    }

    private suspend fun recordOutcome(
        task: ManualTask,
        manifest: SubmissionManifest,
        headSha: String,
        outcome: OperationOutcome,
    ) {
        val (label, key, auditRef) = when (outcome) {
            is OperationOutcome.Succeeded -> Triple("SUCCEEDED", null, outcome.auditRef)
            is OperationOutcome.Refused -> Triple("REFUSED:${outcome.code}", null, outcome.auditRef)
            is OperationOutcome.Conflict -> Triple("CONFLICT:${outcome.code}", null, outcome.auditRef)
            is OperationOutcome.Timeout -> Triple("TIMEOUT", outcome.idempotencyKey, null)
            is OperationOutcome.UncertainCompletion -> Triple("UNCERTAIN", outcome.idempotencyKey, outcome.auditRef)
            is OperationOutcome.RecoveryRequired -> Triple("RECOVERY_REQUIRED", outcome.idempotencyKey, outcome.auditRef)
        }

        recordWorkflowMetadataUseCase(
            taskId = task.id,
            planId = task.planId,
            orderId = task.orderId,
            branch = task.branch,
            headSha = headSha,
            stageId = task.stage?.id ?: "",
            statusWire = task.status.wireValue,
            phaseLabel = "UPLOAD_$label",
            idempotencyKey = key,
            auditRef = auditRef,
            submissionId = manifest.submissionId,
            zipSha256 = manifest.digest.zipSha256,
        )

        recordOperatorActionUseCase(
            taskId = task.id,
            planId = task.planId,
            actionName = "UPLOAD",
            outcomeLabel = label,
            detail = "Upload submission ${manifest.submissionId} (${manifest.digest.zipSha256.take(12)}…) -> $label",
            idempotencyKey = key,
            auditRef = auditRef,
            headSha = headSha,
        )
    }
}
