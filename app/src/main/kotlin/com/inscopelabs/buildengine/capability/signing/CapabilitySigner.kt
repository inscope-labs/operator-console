package com.inscopelabs.buildengine.capability.signing

/**
 * Signs a capability message.
 *
 * Design review §5, decision D-a: this interface is the seam between the two signing
 * directions. DeviceKeyCapabilitySigner is Model B (console signs, gateway verifies —
 * the recommendation); HmacCapabilitySigner is Model A / Lab mode.
 */
interface CapabilitySigner {

    /** The key selector, bound into the signed message. Never a secret. */
    fun kid(): String

    /** Signs the exact bytes of the encoded capability message. */
    fun sign(message: ByteArray): ByteArray
}
