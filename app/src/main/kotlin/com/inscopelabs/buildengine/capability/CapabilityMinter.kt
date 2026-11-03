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
        overrideSigner: CapabilitySigner? = null,
        mintedAtEpochSeconds: Long? = null,
    ): SignedCapability {
        val now = mintedAtEpochSeconds ?: clock.nowEpochSeconds()
        val exp = now + ttlSeconds
        val activeSigner = overrideSigner ?: signer
        val kid = activeSigner.kid()

        val capability = OperatorCapability(
            kid = kid,
            exp = exp,
            order = order,
            plan = plan,
            task = task,
            op = scope.op,
            branch = scope.branch,
            head = scope.head,
            nonce = scope.nonce,
        )

        val message = buildMessage(capability)
        val signature = activeSigner.sign(message.toByteArray(Charsets.UTF_8))
        return SignedCapability(capability, signature)
    }

    fun buildMessage(capability: OperatorCapability): String =
        OperatorCapabilityCodec.encode(capability)
}
