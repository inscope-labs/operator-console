package com.inscopelabs.buildengine.capability.model

/**
 * The four fields that together define what a capability may do.
 *
 * Design review §5: operation, branch, head and nonce are bound into the signature,
 * so a signature cannot be stretched after minting.
 */
data class CapabilityScope(
    val op: OperationType,
    val branch: String,
    val head: String,
    val nonce: String,
) {
    init {
        if (op.isTaskScoped) {
            require(OperatorCapability.TASK_BRANCH_REGEX.matches(branch)) {
                "Task-scoped operation $op requires branch matching ${OperatorCapability.TASK_BRANCH_REGEX}, got '$branch'"
            }
        } else {
            require(OperatorCapability.ENGINE_BRANCH_REGEX.matches(branch)) {
                "Engine-scoped operation $op requires valid branch, got '$branch'"
            }
        }
        require(OperatorCapability.HEAD_REGEX.matches(head)) {
            "Invalid head '$head': must be 40 lowercase hex characters"
        }
        require(OperatorCapability.NONCE_REGEX.matches(nonce)) {
            "Invalid nonce '$nonce': must match ${OperatorCapability.NONCE_REGEX}"
        }
    }
}
