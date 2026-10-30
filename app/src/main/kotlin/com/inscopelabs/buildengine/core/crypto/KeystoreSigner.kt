package com.inscopelabs.buildengine.core.crypto

import com.inscopelabs.buildengine.capability.signing.CapabilitySigner

/**
 * Signs a capability message with the device key.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the signature covers the scope (operation, branch, head, nonce),
 * because the scope is what actually constrains the request.
 */
class KeystoreSigner : CapabilitySigner {

    override fun kid(): String = TODO("SCAFFOLD: return the key selector.")

    override fun sign(message: ByteArray): ByteArray =
        TODO("SCAFFOLD: sign with the non-exportable Keystore private key.")
}
