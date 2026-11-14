package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import java.io.InputStream

/** Target metadata for `POST /tasks/{id}/upload` (§3.10; T3-403, PR3-022). */
data class UploadTarget(
    val taskId: String,
    val plan: String,
    val order: String,
    val branch: String,
    val headSha: String,
    val submissionId: String,
)

sealed interface UploadResult {
    data class Executed(
        val outcome: OperationOutcome,
        val zipSha256: String,
    ) : UploadResult

    data class NotAuthorized(val reason: MintResult) : UploadResult
    data class InvalidTarget(val reason: String) : UploadResult
    data class InvalidArchive(val reason: String) : UploadResult
}

/**
 * Submission intake (`POST /tasks/{id}/upload`, §3.10).
 *
 * Pre-validates and hashes the archive via [SubmissionArchivePreparer] BEFORE minting, never
 * re-packs or mutates the archive bytes, obtains authorization solely through
 * [ConfirmAndMintCapabilityUseCase], and submits through journal-backed [OperationRepository.upload]
 * (where `zipBase64` and capability travel on the wire only).
 */
class UploadSubmissionUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
) {

    suspend operator fun invoke(
        target: UploadTarget,
        archive: PreparedSubmissionArchive,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): UploadResult = submitPrepared(target, archive, typedHeadConfirmation, policy)

    suspend operator fun invoke(
        target: UploadTarget,
        openStream: () -> InputStream?,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): UploadResult {
        val validatedTarget = validateTarget(target)
        if (validatedTarget is TargetValidation.Invalid) {
            return UploadResult.InvalidTarget(validatedTarget.reason)
        }
        return when (val prepared = SubmissionArchivePreparer.prepareFromStream(openStream)) {
            is ArchivePreparationResult.Invalid -> UploadResult.InvalidArchive(prepared.reason)
            is ArchivePreparationResult.Ready -> submitPrepared(target, prepared.archive, typedHeadConfirmation, policy)
        }
    }

    private suspend fun submitPrepared(
        target: UploadTarget,
        archive: PreparedSubmissionArchive,
        typedHeadConfirmation: String?,
        policy: BiometricPolicy,
    ): UploadResult {
        val validation = validateTarget(target)
        if (validation is TargetValidation.Invalid) {
            return UploadResult.InvalidTarget(validation.reason)
        }

        // Re-verify prepared archive integrity before minting to guarantee content-addressed match.
        val recheck = SubmissionArchivePreparer.prepareFromBytes(archive.bytes)
        if (recheck is ArchivePreparationResult.Invalid) {
            return UploadResult.InvalidArchive(recheck.reason)
        }
        val verifiedArchive = (recheck as ArchivePreparationResult.Ready).archive
        if (verifiedArchive.zipSha256 != archive.zipSha256) {
            return UploadResult.InvalidArchive("Archive digest mismatch prior to authorization")
        }

        val request = OperationRequest(
            op = OperationType.UPLOAD,
            scope = validation.let { (it as TargetValidation.Valid).scope },
            plan = target.plan,
            task = target.taskId,
            order = target.order,
            typedHeadConfirmation = typedHeadConfirmation,
        )

        val minted = when (val result = confirmAndMintCapability(request, policy)) {
            is MintResult.Minted -> result
            else -> return UploadResult.NotAuthorized(result)
        }

        val outcome = operationRepository.upload(
            request = request,
            submissionId = target.submissionId.trim(),
            zipSha256 = verifiedArchive.zipSha256,
            zipBase64 = verifiedArchive.zipBase64,
            capability = minted.capability,
        )
        return UploadResult.Executed(outcome = outcome, zipSha256 = verifiedArchive.zipSha256)
    }

    private fun validateTarget(target: UploadTarget): TargetValidation {
        if (
            target.plan.isBlank() ||
            target.taskId.isBlank() ||
            target.order.isBlank() ||
            target.submissionId.isBlank()
        ) {
            return TargetValidation.Invalid("Plan, task, order and submissionId are required")
        }
        val scope = try {
            CapabilityScope(
                op = OperationType.UPLOAD,
                branch = target.branch,
                head = target.headSha,
                nonce = nonceSource.newNonce(),
            )
        } catch (e: IllegalArgumentException) {
            return TargetValidation.Invalid(e.message ?: "Invalid upload scope")
        }
        return TargetValidation.Valid(scope)
    }

    private sealed interface TargetValidation {
        data class Valid(val scope: CapabilityScope) : TargetValidation
        data class Invalid(val reason: String) : TargetValidation
    }
}
