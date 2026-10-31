package com.inscopelabs.buildengine.capability.signing

import com.inscopelabs.buildengine.capability.codec.OperatorCapabilityCodec
import com.inscopelabs.buildengine.capability.model.OperatorCapability
import com.inscopelabs.buildengine.core.crypto.Base64Url

/**
 * A capability together with its signature and wire representation.
 *
 * Design review §8: the signature must never reach a log sink. util/Redactor.kt is
 * the control for that.
 */
class SignedCapability(
    val capability: OperatorCapability,
    val signature: ByteArray,
) {
    val message: String = OperatorCapabilityCodec.encode(capability)

    val signatureBase64Url: String = Base64Url.encode(signature)

    val wire: String = "$message\n$signatureBase64Url"

    fun toWire(): String = wire

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedCapability) return false
        return capability == other.capability && signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = capability.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }

    override fun toString(): String =
        "SignedCapability(capability=$capability, signature=[REDACTED])"
}
