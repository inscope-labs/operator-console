package com.inscopelabs.buildengine.domain.model.preflight

/**
 * Identifiers for the 8 independent preflight checks (Stage 2.4 / T2-401).
 */
enum class PreflightCheckId {
    CONFIGURED,
    REACHABLE,
    TLS_PINNING,
    AUTHENTICATION,
    CLOCK_SKEW,
    CONTRACT_VERSION,
    ENROLMENT_STATE,
    POSTURE_TIER,
}

enum class CheckStatus {
    PENDING,
    RUNNING,
    PASS,
    WARN,
    FAIL,
}

data class PreflightCheckResult(
    val id: PreflightCheckId,
    val title: String,
    val status: CheckStatus,
    val summary: String,
    val detail: String? = null,
    val durationMs: Long? = null,
)

data class PreflightReport(
    val isRunning: Boolean = false,
    val checks: List<PreflightCheckResult> = emptyList(),
    val overallPass: Boolean? = null,
    val expectedEnvironment: String = "test",
    val gatewayBaseUrl: String = "",
    val executedEpochMillis: Long = System.currentTimeMillis(),
)
