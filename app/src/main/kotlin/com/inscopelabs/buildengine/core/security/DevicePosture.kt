package com.inscopelabs.buildengine.core.security

/**
 * The honest security posture of this device.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: these flags must reflect what was actually measured, never what
 * the app would like to claim.
 */
data class DevicePosture(
    val hardwareBackedKey: Boolean = false,
    val strongBoxAvailable: Boolean = false,
    val attested: Boolean = false,
    val certificatePinned: Boolean = false,
)
