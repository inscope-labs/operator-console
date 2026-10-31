package com.inscopelabs.buildengine.capability.model

/**
 * The operator capability — the signed, scoped, expiring authorisation artefact.
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
) {

    init {
        require(KID_REGEX.matches(kid)) { "Invalid kid '$kid': must match $KID_REGEX" }
        require(exp > 0) { "Invalid exp $exp: must be greater than 0" }
        require(order.isNotBlank() && !order.contains('\n') && !order.contains('\r')) {
            "Invalid order '$order': cannot be blank or contain newlines"
        }
        require(plan.isNotBlank() && !plan.contains('\n') && !plan.contains('\r')) {
            "Invalid plan '$plan': cannot be blank or contain newlines"
        }
        require(task.isNotBlank() && !task.contains('\n') && !task.contains('\r')) {
            "Invalid task '$task': cannot be blank or contain newlines"
        }
        if (op.isTaskScoped) {
            require(TASK_BRANCH_REGEX.matches(branch)) {
                "Task-scoped operation $op requires branch matching $TASK_BRANCH_REGEX, got '$branch'"
            }
        } else {
            require(isEngineBranch(branch)) {
                "Engine-scoped operation $op requires an engine branch name (not a task branch), got '$branch'"
            }
        }
        require(HEAD_REGEX.matches(head)) {
            "Invalid head '$head': must be 40 lowercase hex characters"
        }
        require(NONCE_REGEX.matches(nonce)) {
            // The offending value is deliberately not echoed: a nonce is single-use secret material.
            "Invalid nonce: must match $NONCE_REGEX"
        }
    }

    val scope: CapabilityScope
        get() = CapabilityScope(op = op, branch = branch, head = head, nonce = nonce)

    companion object {
        val KID_REGEX: Regex = Regex("^[A-Za-z0-9_]{1,16}$")
        val TASK_BRANCH_REGEX: Regex = Regex("^task-[A-Za-z0-9._-]+$")
        val ENGINE_BRANCH_REGEX: Regex = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*(?:/[A-Za-z0-9][A-Za-z0-9._-]*)*$")
        val HEAD_REGEX: Regex = Regex("^[0-9a-f]{40}$")
        /** docs/gateway-contract.md: the nonce is at least 16 lowercase hex characters. */
        val NONCE_REGEX: Regex = Regex("^[0-9a-f]{16,64}$")

        /**
         * True when [branch] is a plausible engine branch (ADR 0005, D-T2): a safe ref name that
         * is not a task branch. Which engine branch is *configured* is checked by the gateway
         * and by the scope resolver, not by this model.
         */
        fun isEngineBranch(branch: String): Boolean =
            ENGINE_BRANCH_REGEX.matches(branch) &&
                !branch.startsWith("task-") &&
                !branch.contains("..") &&
                !branch.endsWith(".") &&
                !branch.endsWith(".lock")
    }
}
