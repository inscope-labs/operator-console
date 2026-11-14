package com.inscopelabs.buildengine.domain.model

/**
 * Result of `POST /sync` (docs/gateway-contract.md §3.13).
 * Read-only check verifying repository synchronization with upstream tracking.
 */
data class SyncStatus(
    val status: String,
    val headSha: String,
    val inSync: Boolean,
)

/**
 * Result of `GET /doctor` (docs/gateway-contract.md §3.14).
 * Diagnostic report for gateway token, ruleset bypass, and halt state.
 */
data class DoctorReport(
    val healthy: Boolean,
    val checks: List<DoctorCheck>,
)

/**
 * One diagnostic check item inside [DoctorReport].
 */
data class DoctorCheck(
    val name: String,
    val passed: Boolean,
    val details: String = "",
)
