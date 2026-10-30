package com.inscopelabs.buildengine.capability.model

/**
 * The operator capability — the signed, scoped, expiring authorisation artefact.
 *
 * Design review §5: the capability turns "authorise this publish of this branch at
 * this SHA" into a checkable artefact rather than an inference from a login state.
 * The wire format lives in capability/codec/OperatorCapabilityCodec.kt.
 *
 * Four of the nine fields decide what the artefact may do — [op], [branch], [head]
 * and [nonce] — and those four are the ones bound into the signature. That is the
 * point of the shape: a capability cannot be stretched after it is minted, because
 * changing any of them changes the bytes that were signed and the signature stops
 * verifying. The scope *is* the constraint, so the scope is what the signature
 * covers.
 *
 * This is a record of a decision, not the decision's authority. It says what the
 * operator approved; it does not say whether the gateway will honour it, and the
 * console never gets a say in that — the gateway is the sole enforcement point.
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
) {
    /**
     * The part of the capability that decides what it may do.
     *
     * The scope is what the UI shows the operator before they confirm, and what the
     * mint is given in the first place — it is the same four fields, held together
     * so that they cannot be separated on the way to the signature.
     */
    fun toScope(): CapabilityScope = CapabilityScope(
        op = op,
        branch = branch,
        head = head,
        nonce = nonce,
    )
}
