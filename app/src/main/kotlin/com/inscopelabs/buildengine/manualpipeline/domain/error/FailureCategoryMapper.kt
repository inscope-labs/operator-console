package com.inscopelabs.buildengine.manualpipeline.domain.error

import com.inscopelabs.buildengine.manualpipeline.domain.model.FailureCategory
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Maps raw gateway error codes and transport states onto [FailureCategory].
 */
object FailureCategoryMapper {

    /**
     * Maps a gateway refusal code onto a [FailureCategory].
     *
     * The gateway's code is the primary signal: it is what the operator has to act on,
     * so it is never flattened into a generic failure (operator-guide.md §4.3). The
     * message is only consulted when the code alone does not identify the class.
     */
    fun fromGatewayCode(code: String?, message: String = ""): FailureCategory {
        val norm = code?.trim()?.uppercase().orEmpty()
        return when {
            norm == "NOT_CONFIGURED" || norm == "not_configured".uppercase() -> FailureCategory.NOT_CONFIGURED
            norm == "UNREACHABLE" || norm == "NETWORK" -> FailureCategory.UNREACHABLE
            norm == "TIMEOUT" || norm == "TIMED_OUT" || norm == "GATEWAY_TIMEOUT" -> FailureCategory.UNREACHABLE
            norm == "TLS_PINNING" -> FailureCategory.TLS_PINNING
            norm in AUTH_CODES -> FailureCategory.AUTH
            norm in STALE_CODES -> FailureCategory.STALE
            norm in CONFLICT_CODES -> FailureCategory.CONFLICT
            norm in POLICY_CODES -> FailureCategory.POLICY_REFUSED
            message.contains("not configured", ignoreCase = true) -> FailureCategory.NOT_CONFIGURED
            message.contains("pin", ignoreCase = true) || message.contains("tls", ignoreCase = true) -> FailureCategory.TLS_PINNING
            else -> FailureCategory.UNKNOWN
        }
    }

    /**
     * Maps a transport or gateway exception onto a [FailureCategory].
     *
     * A [ManualPipelineException] already carries the category the module decided on,
     * so it is taken as-is. Transport failures are classified by their type, and
     * anything else falls back to the message heuristics in [fromGatewayCode].
     *
     * An unclassifiable failure is [FailureCategory.UNKNOWN] rather than a guess:
     * the console fails closed and never reports a refusal it did not read (I-08).
     */
    fun fromThrowable(error: Throwable?): FailureCategory {
        if (error == null) return FailureCategory.UNKNOWN
        if (error is ManualPipelineException) return error.category
        if (error is SSLPeerUnverifiedException) return FailureCategory.TLS_PINNING
        if (error is UnknownHostException) return FailureCategory.UNREACHABLE
        if (error is IOException) return FailureCategory.UNREACHABLE
        return fromGatewayCode(null, error.message.orEmpty())
    }

    private val AUTH_CODES = setOf(
        "AUTH",
        "AUTHENTICATION",
        "OPERATOR_AUTH",
        "CREDENTIAL_NOT_ACCEPTED",
        "NOT_ENROLLED",
        "DEVICE_NOT_ENROLLED",
        "UNKNOWN_KID",
        "BAD_SIGNATURE",
        "AUTH_TIER_NOT_ALLOWED",
    )

    private val STALE_CODES = setOf(
        "STALE",
        "HEAD_DRIFT",
        "HEAD_DRIFTED",
        "HEAD_MOVED",
        "EXPIRED",
        "CAPABILITY_EXPIRED",
    )

    private val CONFLICT_CODES = setOf(
        "CONFLICT",
        "NONCE_REPLAYED",
        "REPLAYED",
        "SUBMISSION_IMMUTABLE",
        "ANCESTRY_FAIL",
        "RECONCILE_REQUIRED",
        "IDEMPOTENCY_KEY_REUSED",
    )

    private val POLICY_CODES = setOf(
        "POLICY_REFUSED",
        "GUARD_FAILED",
        "SCOPE_REFUSED",
        "HALT_ACTIVE",
        "ENVIRONMENT_MISMATCH",
        "REPO_NOT_PRIVATE",
        "DELTA_EMPTY",
        "MERGE_COMMITS",
        "DENIED_PATH",
        "SECRET_IN_DIFF",
        "RULESET_REJECTED",
        "BRANCH_MISMATCH",
        "BRANCH_NOT_TASK",
        "BRANCH_INVALID",
        // Seal and transition refusals. An unavailable seal fact is a refusal, never an
        // assumption (I-08), and an illegal transition is a guard refusal.
        "SEAL_NOT_ATTESTED",
        "SEAL_FACT_UNAVAILABLE",
        "TRANSITION_NOT_ALLOWED",
    )
}
