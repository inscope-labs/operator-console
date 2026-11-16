package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Distinct failure classification aligned with ErrorMapper & contract §4.1.
 */
enum class FailureCategory {
    NOT_CONFIGURED,
    UNREACHABLE,
    TLS_PINNING,
    AUTH,
    STALE,
    POLICY_REFUSED,
    CONFLICT,
    UNKNOWN;
}
