package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Supplies the single-use nonce that makes a capability non-replayable.
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

    private val issuedNonces = ConcurrentHashMap.newKeySet<String>()

    override fun newNonce(): String {
        while (true) {
            val candidate = secureRandomSource.nextNonce(16)
            if (issuedNonces.add(candidate)) {
                return candidate
            }
        }
    }
}
