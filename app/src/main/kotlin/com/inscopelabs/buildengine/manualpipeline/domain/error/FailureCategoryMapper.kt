package com.inscopelabs.buildengine.manualpipeline.domain.error

import com.inscopelabs.buildengine.manualpipeline.domain.model.FailureCategory

/**
 * Maps raw gateway error codes and transport states onto [FailureCategory].
 */
object FailureCategoryMapper {

    fun fromCode(code: String?, message: String = ""): FailureCategory {
        val norm = code?.trim()?.uppercase().orEmpty()
        return when {
            norm == "NOT_CONFIGURED" || norm == "not_configured".uppercase() -> FailureCategory.NOT_CONFIGURED
            norm == "UNREACHABLE" || norm == "NETWORK" -> FailureCategory.UNREACHABLE
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
    )
}
