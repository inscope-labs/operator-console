package com.inscopelabs.buildengine.manualpipeline.domain.validation

import com.inscopelabs.buildengine.manualpipeline.domain.error.GatewayRefusal
import javax.inject.Inject

/**
 * Detects seal / immutability state from prior handoff evidence or gateway refusal (`SUBMISSION_IMMUTABLE`).
 */
class SealStateValidator @Inject constructor() {

    data class Result(
        val sealed: Boolean,
        val submissionId: String?,
        val reason: String,
    )

    fun check(
        lastRefusal: GatewayRefusal? = null,
        existingSubmissionId: String? = null,
        alreadyUploadedSha256: String? = null,
        candidateSha256: String? = null,
    ): Result {
        if (lastRefusal != null && lastRefusal.isSealRefusal) {
            return Result(
                sealed = true,
                submissionId = existingSubmissionId,
                reason = "Gateway sealed this submission (${lastRefusal.code}): ${lastRefusal.message}",
            )
        }
        if (!alreadyUploadedSha256.isNullOrBlank() &&
            !candidateSha256.isNullOrBlank() &&
            !alreadyUploadedSha256.equals(candidateSha256, ignoreCase = true)
        ) {
            return Result(
                sealed = true,
                submissionId = existingSubmissionId,
                reason = "An immutable archive digest ($alreadyUploadedSha256) was already uploaded for this submission.",
            )
        }
        return Result(
            sealed = false,
            submissionId = existingSubmissionId,
            reason = "No submission seal conflict recorded locally.",
        )
    }
}
