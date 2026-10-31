package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The gateway's diagnostic report.
 *
 * docs/gateway-contract.md §3.14 — `GET /doctor`.
 *
 * The `doctor` is the `assert_private` preflight and the ruleset check: it says
 * whether the gateway can do its job at all. It is read-only and needs no
 * capability, and it is the call the operator makes when something has gone wrong.
 */
@Serializable
data class DoctorReportDto(
    val healthy: Boolean = false,
    val checks: List<DoctorCheckDto> = emptyList(),
)

/**
 * One diagnostic check inside a [DoctorReportDto].
 */
@Serializable
data class DoctorCheckDto(
    val name: String = "",
    val passed: Boolean = false,
    val details: String = "",
)
