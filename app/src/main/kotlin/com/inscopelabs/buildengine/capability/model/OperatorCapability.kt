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
            require(ENGINE_BRANCH_REGEX.matches(branch) && !branch.contains('\n') && !branch.contains('\r')) {
                "Engine-scoped operation $op requires branch matching $ENGINE_BRANCH_REGEX, got '$branch'"
            }
        }
        require(HEAD_REGEX.matches(head)) {
            "Invalid head '$head': must be 40 lowercase hex characters"
        }
        require(NONCE_REGEX.matches(nonce)) {
            "Invalid nonce '$nonce': must match $NONCE_REGEX"
        }
    }

    val scope: CapabilityScope
        get() = CapabilityScope(op = op, branch = branch, head = head, nonce = nonce)

    companion object {
        val KID_REGEX: Regex = Regex("^[A-Za-z0-9_]{1,16}$")
        val TASK_BRANCH_REGEX: Regex = Regex("^task-[A-Za-z0-9._-]+$")
        val ENGINE_BRANCH_REGEX: Regex = Regex("^[A-Za-z0-9._/-]+$")
        val HEAD_REGEX: Regex = Regex("^[0-9a-f]{40}$")
        val NONCE_REGEX: Regex = Regex("^[A-Za-z0-9._~+/-]+$")
    }
}
