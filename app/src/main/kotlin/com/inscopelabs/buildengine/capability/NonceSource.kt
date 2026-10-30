package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource

/**
 * Supplies the single-use nonce that makes a capability non-replayable.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the gateway stores the nonce until expiry and refuses one it has
 * already seen.
 */
interface NonceSource {
    fun newNonce(): String
}

class DeviceNonceSource(
    private val secureRandomSource: SecureRandomSource,
) : NonceSource {
    override fun newNonce(): String = TODO("SCAFFOLD: delegate to the CSPRNG source.")
}
