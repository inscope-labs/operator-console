package com.inscopelabs.buildengine.core.security

/** Facts measured from the current device/key; null means not measured or unavailable. */
data class DevicePosture(
    val hardwareBackedKey: Boolean,
    val strongBoxAvailable: Boolean,
    val securityLevel: String,
    val keyAttestationAvailable: Boolean,
    /** Play Integrity is intentionally deferred under D-T4. */
    val playIntegrityAvailable: Boolean = false,
) {
    val isStrongBoxBacked: Boolean get() = securityLevel == "STRONGBOX"
}
