package com.inscopelabs.buildengine.capability

/**
 * Supplies the single-use nonce that makes a capability non-replayable.
 *
 * Design review §5: the gateway stores the nonce until expiry and refuses one it has
 * already seen.
 *
 * The interface lives here and the CSPRNG lives in `core.crypto`, so that `core`
 * never has to depend upward on `capability`. The implementation that joins them is
 * `DeviceNonceSource`, in this package.
 */
interface NonceSource {
    fun newNonce(): String
}
