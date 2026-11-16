package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest

/**
 * Port delegating capability minting to the host's single [ConfirmAndMintCapabilityUseCase]
 * path (I-01, ADR 0012). Raw cryptographic capability strings never enter the domain layer.
 */
interface MintingPort {
    suspend fun mintForTransition(request: TransitionRequest): MintOutcome

    suspend fun mintForUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        typedHeadConfirmation: String? = null,
    ): MintOutcome
}

data class MintedCapabilityHandle(
    val handleId: String,
    val operation: String,
    val planId: String,
    val taskId: String,
    val orderId: String,
    val branch: String,
    val headSha: String,
    val ttlSeconds: Long,
    val tier: ConfirmationTier,
)

sealed interface MintOutcome {
    data class Minted(val handle: MintedCapabilityHandle) : MintOutcome
    data class HeadDrifted(val liveSha: String) : MintOutcome
    data class HeadUnavailable(val reason: String) : MintOutcome
    data object Declined : MintOutcome
    data class AuthFailed(val reason: String) : MintOutcome
    data object LockedOut : MintOutcome
    data object KeyInvalidated : MintOutcome
    data object NotEnrolled : MintOutcome
    data object ExpiredWhileConfirming : MintOutcome
    data class TierNotAllowed(val reason: String) : MintOutcome
    data class EnvironmentMismatch(val expected: String, val reported: String) : MintOutcome
}
