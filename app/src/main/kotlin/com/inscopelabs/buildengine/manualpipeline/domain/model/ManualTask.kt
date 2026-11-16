package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Manual pipeline task aggregate carrying plan, work-order identifier, stage,
 * canonical Gateway A status, target branch, and live 40-char head SHA.
 */
data class ManualTask(
    val id: String,
    val planId: String,
    val title: String,
    val orderId: String,
    val stage: ImplementationStage?,
    val status: TaskStatus,
    val branch: String,
    val headSha: String,
    val isPlanHalted: Boolean = false,
    val isBlockedByGateway: Boolean = false,
) {
    val hasOrderId: Boolean get() = orderId.isNotBlank()
    val hasValidHeadSha: Boolean get() = FULL_SHA_REGEX.matches(headSha)

    companion object {
        private val FULL_SHA_REGEX = Regex("^[0-9a-fA-F]{40}$")
    }
}
