package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import com.inscopelabs.buildengine.core.security.DeviceAttestation
import com.inscopelabs.buildengine.domain.model.SecurityPosture

/**
 * Reports what this device can honestly claim.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: "hardware-backed" is not a claim the app can make
 * unconditionally. This use case must report the measured result.
 */
class ReportDevicePostureUseCase(
    private val strongBoxSupport: StrongBoxSupport,
    private val deviceAttestation: DeviceAttestation,
) {

    suspend operator fun invoke(keyAlias: String): SecurityPosture =
        TODO("SCAFFOLD: measure the posture and report it honestly.")
}
