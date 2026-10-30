package com.inscopelabs.buildengine.capability

/**
 * Supplies the single-use nonce that makes a capability non-replayable.
 *
 * Design review §5: the gateway stores the nonce until expiry and refuses one it has
 * already seen.
 */
interface NonceSource {
    fun newNonce(): String
}
