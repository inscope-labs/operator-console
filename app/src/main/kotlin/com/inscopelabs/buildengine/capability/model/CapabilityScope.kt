package com.inscopelabs.buildengine.capability.model

/**
 * The four fields that together define what a capability may do.
 *
 * Design review §5: operation, branch, head and nonce are bound into the signature,
 * so a signature cannot be stretched after minting.
 *
 * They are held together because they travel together: the scope is what the
 * operator is shown before they confirm, and it is the same four fields that go
 * into the signed message. Splitting them would make it possible to show one set
 * of fields and sign another.
 */
data class CapabilityScope(
    val op: OperationType,
    val branch: String,
    val head: String,
    val nonce: String,
)
