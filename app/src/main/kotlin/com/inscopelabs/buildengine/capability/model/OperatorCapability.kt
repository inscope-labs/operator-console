package com.inscopelabs.buildengine.capability.model

/**
 * The operator capability — the signed, scoped, expiring authorisation artefact.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the capability turns "authorise this publish of this branch at
 * this SHA" into a checkable artefact rather than an inference from a login state.
 * The wire format lives in capability/codec/OperatorCapabilityCodec.kt.
 */
data class OperatorCapability(
    val kid: String,
    val exp: Long,
    val order: String,
    val plan: String,
    val task: String,
    val op: OperationType,
    val branch: String,
    val head: String,
    val nonce: String,
)
