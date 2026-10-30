package com.inscopelabs.buildengine.domain.model

import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType

/**
 * A privileged operation, before confirmation.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: this is what the console shows the operator before a capability
 * is minted — the operation, the branch, the exact head SHA and the TTL.
 */
data class OperationRequest(
    val op: OperationType,
    val scope: CapabilityScope,
    val plan: String,
    val task: String,
    val order: String = "",
)

/**
 * The outcome of a privileged operation.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
data class OperationResult(
    val accepted: Boolean,
    val operation: OperationType,
    val branch: String = "",
    val headSha: String = "",
    val message: String = "",
    val auditRef: String = "",
)
