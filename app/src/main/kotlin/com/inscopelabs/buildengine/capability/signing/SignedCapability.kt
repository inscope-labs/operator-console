package com.inscopelabs.buildengine.capability.signing

import com.inscopelabs.buildengine.capability.model.OperatorCapability

/**
 * A capability together with its signature.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §8: the signature must never reach a log sink. util/Redactor.kt is
 * the control for that.
 */
class SignedCapability(
    val capability: OperatorCapability,
    val signature: ByteArray,
)
