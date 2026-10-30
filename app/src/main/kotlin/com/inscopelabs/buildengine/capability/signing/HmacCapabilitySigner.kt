package com.inscopelabs.buildengine.capability.signing

/**
 * Model A — the gateway mints, the console holds the key.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: kept behind the CapabilitySigner interface until decision D-a is
 * closed. A capability would then exist before the operator decides, which conflicts
 * with a confirmation-driven flow.
 */
class HmacCapabilitySigner : CapabilitySigner {

    override fun kid(): String = TODO("SCAFFOLD: return the key selector.")

    override fun sign(message: ByteArray): ByteArray =
        TODO("SCAFFOLD: sign with the shared HMAC key.")
}
