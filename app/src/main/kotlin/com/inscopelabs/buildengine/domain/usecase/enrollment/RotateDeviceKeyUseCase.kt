package com.inscopelabs.buildengine.domain.usecase.enrollment

import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager

/**
 * Rotates the device key.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: capabilities signed under a retired key stop working.
 */
class RotateDeviceKeyUseCase(private val deviceKeyManager: DeviceKeyManager) {

    suspend operator fun invoke(oldAlias: String, newAlias: String): String =
        TODO("SCAFFOLD: create the new key, enrol it, retire the old one.")
}
