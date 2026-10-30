package com.inscopelabs.buildengine.capability.model

/**
 * The four fields that together define what a capability may do.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: operation, branch, head and nonce are bound into the signature,
 * so a signature cannot be stretched after minting.
 */
data class CapabilityScope(
    val op: OperationType,
    val branch: String,
    val head: String,
    val nonce: String,
)
