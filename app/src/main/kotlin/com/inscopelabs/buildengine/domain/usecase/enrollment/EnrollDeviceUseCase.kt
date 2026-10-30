package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec

/**
 * Creates the device keypair and enrols it with the gateway.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: Model B needs a device enrolment registry. See
 * docs/device-enrollment.md.
 */
class EnrollDeviceUseCase(private val deviceKeyManager: DeviceKeyManager) {

    suspend operator fun invoke(alias: String): String =
        TODO("SCAFFOLD: create the keypair and register the public key.")
}
