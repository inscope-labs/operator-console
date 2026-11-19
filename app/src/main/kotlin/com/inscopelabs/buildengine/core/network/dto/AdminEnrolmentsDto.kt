package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * One enrolled device entry returned by `GET /admin/enrolments` (Stage 4.3 admin surface).
 *
 * Public keys are intentionally omitted by the gateway and never carried here.
 */
@Serializable
data class EnrolledDeviceDto(
    val kid: String = "",
    val alias: String = "",
    val authTier: String = "",
    val status: String = "",
    val enrolledAt: Long = 0L,
)

/**
 * Envelope returned by `GET /admin/enrolments`.
 */
@Serializable
data class AdminEnrolmentsResponseDto(
    val enrolments: List<EnrolledDeviceDto> = emptyList(),
    val count: Int = 0,
)
