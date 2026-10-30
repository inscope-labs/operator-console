package com.inscopelabs.buildengine.domain.model

/**
 * The security posture the console reports about itself.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: this must state what is actually true. Claiming hardware
 * backing on a device that does not have it is a misrepresentation.
 */
data class SecurityPosture(
    val hardwareBacked: Boolean,
    val strongBoxAvailable: Boolean,
    val attested: Boolean,
    val pinned: Boolean,
    val summary: String,
)
