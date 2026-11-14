package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.capability.NonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.security.BiometricPolicy
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase
import com.inscopelabs.buildengine.domain.usecase.common.MintResult

/** What the operator is publishing: one task branch at one exact head. */
data class PublishTarget(
    val taskId: String,
    val plan: String,
    val order: String,
    val branch: String,
    val headSha: String,
)

/** The result of a publish attempt, separating "nothing was sent" from "something was sent". */
sealed interface PublishResult {

    /** A request was authorised and sent; [outcome] says what is actually known (G-08). */
    data class Executed(val outcome: OperationOutcome) : PublishResult

    /** The confirmation path did not produce a capability. Nothing was sent. */
    data class NotAuthorized(val reason: MintResult) : PublishResult

    /** The target could not form a valid capability scope. Nothing was sent. */
    data class InvalidTarget(val reason: String) : PublishResult
}

/**
 * Publishes a minted branch to the public repository.
 *
 * Design review §12: this is the only action that crosses to the public repository.
 * The gateway runs the full guard set on the write; this use case does not evaluate guards.
 *
 * Sequence: build the scope (fresh single-use nonce) -> confirmation path (head check,
 * operator confirmation, capability creation) -> journal-backed send -> typed outcome.
 * The capability is created only by [ConfirmAndMintCapabilityUseCase]; this class never
 * creates one itself.
 */
class PublishTaskUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
) {

    suspend operator fun invoke(
        target: PublishTarget,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): PublishResult {
        val scope = try {
            CapabilityScope(
                op = OperationType.PUBLISH,
                branch = target.branch,
                head = target.headSha,
                nonce = nonceSource.newNonce(),
            )
        } catch (e: IllegalArgumentException) {
            return PublishResult.InvalidTarget(e.message ?: "Invalid publish target")
        }
        if (target.plan.isBlank() || target.taskId.isBlank() || target.order.isBlank()) {
            return PublishResult.InvalidTarget("Plan, task and order identifiers are required")
        }

        val request = OperationRequest(
            op = OperationType.PUBLISH,
            scope = scope,
            plan = target.plan,
            task = target.taskId,
            order = target.order,
            typedHeadConfirmation = typedHeadConfirmation,
        )

        return when (val minted = confirmAndMintCapability(request, policy)) {
            is MintResult.Minted ->
                PublishResult.Executed(operationRepository.publish(request, minted.capability))
            else -> PublishResult.NotAuthorized(minted)
        }
    }
}
