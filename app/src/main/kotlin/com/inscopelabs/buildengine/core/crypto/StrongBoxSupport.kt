package com.inscopelabs.buildengine.core.crypto

/**
 * Reports the real hardware-backing state of a Keystore key.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: "hardware-backed" is not a claim the app may make unconditionally.
 * This class must read KeyInfo.isInsideSecureHardware (and the security level where
 * available) and report the actual result — see docs/device-enrollment.md.
 */
class StrongBoxSupport {

    fun isStrongBoxAvailable(): Boolean = TODO("SCAFFOLD: query StrongBox availability.")

    fun isHardwareBacked(alias: String): Boolean =
        TODO("SCAFFOLD: read KeyInfo.isInsideSecureHardware.")

    fun securityLevel(alias: String): String =
        TODO("SCAFFOLD: read KeyInfo.getSecurityLevel() where available.")
}
