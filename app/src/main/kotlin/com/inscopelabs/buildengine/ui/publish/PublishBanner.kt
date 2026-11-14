package com.inscopelabs.buildengine.ui.publish

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayFailureState
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.domain.usecase.write.PublishResult

/**
 * What the operator is told about a publish attempt.
 *
 * Design review §12, Appendix B.3: a refused scope, a replayed capability, a drifted head and
 * an active halt are four different things and must not collapse into one message.
 */
enum class PublishBannerKind {
    SUCCEEDED,
    SCOPE_REFUSED,
    REPLAYED,
    HEAD_DRIFTED,
    HALT_ACTIVE,
    REFUSED,
    CONFLICT,
    TIMEOUT,
    UNCERTAIN,
    RECOVERY_REQUIRED,
    DECLINED,
    AUTH_FAILED,
    LOCKED_OUT,
    KEY_INVALIDATED,
    NOT_ENROLLED,
    TTL_EXPIRED,
    TIER_NOT_ALLOWED,
    ENVIRONMENT_MISMATCH,
    HEAD_UNAVAILABLE,
    INVALID_TARGET,
}

data class PublishBanner(
    val kind: PublishBannerKind,
    /** Contract error code, when the gateway supplied one. */
    val code: String? = null,
    /** Audit reference, shown whenever the gateway supplied one. */
    val auditRef: String? = null,
    /** Extra detail such as a live head SHA or a local reason. */
    val detail: String? = null,
) {
    /** Only a verified success is a success. Uncertain is not one (G-08). */
    val isSuccess: Boolean get() = kind == PublishBannerKind.SUCCEEDED
}

object PublishBannerPresenter {

    private val errorMapper = ErrorMapper()

    fun present(result: PublishResult): PublishBanner = when (result) {
        is PublishResult.Executed -> presentOutcome(result.outcome)
        is PublishResult.NotAuthorized -> presentMint(result.reason)
        is PublishResult.InvalidTarget ->
            PublishBanner(PublishBannerKind.INVALID_TARGET, detail = result.reason)
    }

    fun presentOutcome(outcome: OperationOutcome): PublishBanner = when (outcome) {
        is OperationOutcome.Succeeded ->
            PublishBanner(PublishBannerKind.SUCCEEDED, auditRef = outcome.auditRef, detail = outcome.headSha)
        is OperationOutcome.Refused ->
            PublishBanner(kindForCode(outcome.code, PublishBannerKind.REFUSED), outcome.code, outcome.auditRef)
        is OperationOutcome.Conflict ->
            PublishBanner(kindForCode(outcome.code, PublishBannerKind.CONFLICT), outcome.code, outcome.auditRef)
        is OperationOutcome.Timeout ->
            PublishBanner(PublishBannerKind.TIMEOUT)
        is OperationOutcome.UncertainCompletion ->
            PublishBanner(PublishBannerKind.UNCERTAIN, auditRef = outcome.auditRef, detail = outcome.headSha)
        is OperationOutcome.RecoveryRequired ->
            PublishBanner(PublishBannerKind.RECOVERY_REQUIRED, outcome.code, outcome.auditRef)
    }

    private fun kindForCode(code: String, fallback: PublishBannerKind): PublishBannerKind =
        when (errorMapper.stateFor(code)) {
            GatewayFailureState.SCOPE_REFUSED -> PublishBannerKind.SCOPE_REFUSED
            GatewayFailureState.REPLAYED -> PublishBannerKind.REPLAYED
            GatewayFailureState.HEAD_DRIFTED -> PublishBannerKind.HEAD_DRIFTED
            GatewayFailureState.HALT_ACTIVE -> PublishBannerKind.HALT_ACTIVE
            else -> fallback
        }

    private fun presentMint(reason: MintResult): PublishBanner = when (reason) {
        is MintResult.Minted -> error("A minted capability is not a refusal")
        is MintResult.HeadDrifted -> PublishBanner(PublishBannerKind.HEAD_DRIFTED, detail = reason.liveSha)
        is MintResult.HeadUnavailable -> PublishBanner(PublishBannerKind.HEAD_UNAVAILABLE, detail = reason.error)
        MintResult.Declined -> PublishBanner(PublishBannerKind.DECLINED)
        is MintResult.AuthFailed -> PublishBanner(PublishBannerKind.AUTH_FAILED, detail = reason.error)
        MintResult.LockedOut -> PublishBanner(PublishBannerKind.LOCKED_OUT)
        MintResult.KeyInvalidated -> PublishBanner(PublishBannerKind.KEY_INVALIDATED)
        MintResult.NotEnrolled -> PublishBanner(PublishBannerKind.NOT_ENROLLED)
        MintResult.ExpiredWhileConfirming -> PublishBanner(PublishBannerKind.TTL_EXPIRED)
        is MintResult.TierNotAllowed -> PublishBanner(PublishBannerKind.TIER_NOT_ALLOWED, detail = reason.reason)
        is MintResult.EnvironmentMismatch ->
            PublishBanner(PublishBannerKind.ENVIRONMENT_MISMATCH, detail = "${reason.expected} / ${reason.reported}")
    }
}
