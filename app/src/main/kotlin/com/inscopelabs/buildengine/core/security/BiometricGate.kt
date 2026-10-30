package com.inscopelabs.buildengine.core.security

/**
 * Gates capability minting behind an explicit operator confirmation.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5, §13: no capability exists until the operator has confirmed. The
 * gate must be CryptoObject-bound so the unlock authorises the signing operation
 * itself, not a general-purpose "the screen was unlocked" flag.
 */
class BiometricGate {

    /** Returns true when the operator authenticated under [policy]. */
    suspend fun authenticate(policy: BiometricPolicy): Boolean =
        TODO("SCAFFOLD: run BiometricPrompt with the given policy.")

    /** Mirrors BiometricManager.canAuthenticate() for the given policy. */
    fun canAuthenticate(policy: BiometricPolicy): Int =
        TODO("SCAFFOLD: report whether the device can satisfy the policy.")
}
