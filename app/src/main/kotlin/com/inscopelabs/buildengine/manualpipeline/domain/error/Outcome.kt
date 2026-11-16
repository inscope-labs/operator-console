package com.inscopelabs.buildengine.manualpipeline.domain.error

/**
 * Typed operation outcome mirror in `domain.error` for uniform state machine & use-case handling.
 */
sealed interface Outcome {
    data class Succeeded(
        val auditRef: String,
        val headSha: String,
        val detail: String? = null,
    ) : Outcome

    data class Refused(
        val refusal: GatewayRefusal,
    ) : Outcome

    data class Conflict(
        val refusal: GatewayRefusal,
        val liveHeadSha: String? = null,
    ) : Outcome

    data class Timeout(
        val idempotencyKey: String,
    ) : Outcome

    data class UncertainCompletion(
        val idempotencyKey: String,
        val headSha: String? = null,
        val auditRef: String? = null,
    ) : Outcome

    data class RecoveryRequired(
        val idempotencyKey: String,
        val code: String? = null,
        val auditRef: String? = null,
    ) : Outcome
}
