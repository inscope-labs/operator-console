package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.capability.codec.OperatorCapabilityCodec
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperatorCapability
import com.inscopelabs.buildengine.capability.signing.CapabilitySigner
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.core.time.Clock

/**
 * Builds and signs an operator capability.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the suggested operator TTL is five minutes — long enough for a
 * confirmation gesture, short enough that a captured capability is near-useless.
 */
class CapabilityMinter(
    private val signer: CapabilitySigner,
    private val nonceSource: NonceSource,
    private val clock: Clock,
) {

    /** The capability TTL in seconds. Design review §5, decision D-b. */
    val ttlSeconds: Long = 300L

    fun mint(
        scope: CapabilityScope,
        order: String,
        plan: String,
        task: String,
    ): SignedCapability = TODO("SCAFFOLD: build, sign and return the capability.")

    fun buildMessage(capability: OperatorCapability): String =
        TODO("SCAFFOLD: delegate to OperatorCapabilityCodec.message() — the signed bytes are the message, not the transmission.")
}
