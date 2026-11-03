package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.SecurityPosture

/** Returns measured local posture; unavailable measurements are never upgraded to true. */
class ReportDevicePostureUseCase(
    private val strongBoxSupport: StrongBoxSupport,
    private val deviceAttestation: DeviceAttestation,
    private val enrollmentStore: DeviceEnrollmentStore? = null,
) {
    suspend operator fun invoke(keyAlias: String): SecurityPosture {
        val hardwareBacked = runCatching { strongBoxSupport.isHardwareBacked(keyAlias) }.getOrDefault(false)
        val strongBoxAvailable = strongBoxSupport.isStrongBoxAvailable()
        val securityLevel = runCatching { strongBoxSupport.securityLevel(keyAlias) }.getOrDefault("UNKNOWN")
        val keyAttested = deviceAttestation.hasKeyAttestation(keyAlias)

        val activeTierStr = enrollmentStore?.activeRecord()?.authTier
        val authTier = activeTierStr?.let { AuthTier.fromWireValue(it) }

        val summary = buildString {
            append(if (hardwareBacked) "Hardware-backed key" else "Software-backed or unverified key")
            append(" · security level: ").append(securityLevel)
            append(" · StrongBox ").append(if (strongBoxAvailable) "available" else "unavailable")
            append(" · key attestation ").append(if (keyAttested) "present" else "unavailable")
            append(" · Play Integrity deferred")
        }
        return SecurityPosture(
            hardwareBacked = hardwareBacked,
            strongBoxAvailable = strongBoxAvailable,
            attested = keyAttested,
            pinned = false,
            summary = summary,
            securityLevel = securityLevel,
            playIntegrityAvailable = false,
            authTier = authTier,
        )
    }
}
