package com.inscopelabs.buildengine.domain.model

/** Security posture derived from local measurements, not app configuration claims. */
data class SecurityPosture(
    val hardwareBacked: Boolean,
    val strongBoxAvailable: Boolean,
    val attested: Boolean,
    val pinned: Boolean,
    val summary: String,
    val securityLevel: String = "UNKNOWN",
    val playIntegrityAvailable: Boolean = false,
)
