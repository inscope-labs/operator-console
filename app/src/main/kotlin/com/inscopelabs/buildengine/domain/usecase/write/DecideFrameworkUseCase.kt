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
import com.inscopelabs.buildengine.util.Redactor

enum class FrameworkDecision(val wireValue: String) {
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    DEFERRED("DEFERRED"),
}

/**
 * Target for a framework decision (`POST /plans/{id}/decide`, §3.18; T3-404, PR3-025).
 *
 * Per ADR 0014 D2 (documented reduction): the wire request carries only schema fields
 * (`plan`, `task`, `branch`, `decision`, `notes`, `idempotencyKey`, `capability`).
 * Rationale is carried in `notes`; attribution is the enrolled key identifier recorded
 * by the gateway audit path; there is no separate evidence field on the wire.
 * Local fields `headSha` and `order` are retained solely for capability minting and
 * confirmation display; they are never sent as unapproved request properties.
 */
data class DecideTarget(
    val plan: String,
    val task: String,
    val branch: String,
    val headSha: String,
    val order: String,
    val decision: FrameworkDecision,
    /** Rationale / free-text notes sent as the schema `notes` field (ADR 0014 D2). */
    val notes: String? = null,
)

sealed interface DecideResult {
    data class Executed(val outcome: OperationOutcome) : DecideResult

    /**
     * Retained for regression tests that explicitly construct the use case with
     * [contractSchemaSettled] = false. Production DI uses the settled default.
     */
    data class ContractBlocked(val reason: String) : DecideResult
    data class NotAuthorized(val reason: MintResult) : DecideResult
    data class InvalidTarget(val reason: String) : DecideResult
}

/**
 * Resolves a framework decision (`POST /plans/{id}/decide`, §3.18).
 *
 * ADR 0014 D2 settled the documented reduction: request body matches `decide.schema.json`
 * (`decision` + optional `notes` plus schema scope fields and `idempotencyKey`).
 * Attribution is derived server-side from the enrolled key; rationale lives in `notes`.
 * Default [contractSchemaSettled] is now `true` so the production path is operational.
 */
class DecideFrameworkUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
    private val contractSchemaSettled: Boolean = true,
) {

    val isContractSettled: Boolean
        get() = contractSchemaSettled

    suspend operator fun invoke(
        target: DecideTarget,
        typedHeadConfirmation: String? = null,
        policy: BiometricPolicy = BiometricPolicy(),
    ): DecideResult {
        if (
            target.plan.isBlank() ||
            target.task.isBlank() ||
            target.order.isBlank() ||
            target.branch.isBlank() ||
            target.headSha.isBlank()
        ) {
            return DecideResult.InvalidTarget(
                "Plan, task, order, branch and head SHA are required for capability minting",
            )
        }
        val textInputs = listOfNotNull(target.notes)
        if (textInputs.any { Redactor.containsForbidden(it) }) {
            return DecideResult.InvalidTarget(
                "Decision notes must not contain forbidden credential or signature patterns",
            )
        }

        val capabilityScope = try {
            CapabilityScope(
                op = OperationType.DECIDE,
                branch = target.branch,
                head = target.headSha,
                nonce = nonceSource.newNonce(),
            )
        } catch (e: IllegalArgumentException) {
            return DecideResult.InvalidTarget(e.message ?: "Invalid decision scope")
        }

        if (!contractSchemaSettled) {
            return DecideResult.ContractBlocked(PR3_003_BLOCKER_MESSAGE)
        }

        val request = OperationRequest(
            op = OperationType.DECIDE,
            scope = capabilityScope,
            plan = target.plan,
            task = target.task,
            order = target.order,
            typedHeadConfirmation = typedHeadConfirmation,
        )

        val minted = when (val result = confirmAndMintCapability(request, policy)) {
            is MintResult.Minted -> result
            else -> return DecideResult.NotAuthorized(result)
        }

        val outcome = operationRepository.decide(
            request = request,
            decision = target.decision.wireValue,
            notes = target.notes,
            capability = minted.capability,
        )
        return DecideResult.Executed(outcome)
    }

    companion object {
        const val PR3_003_BLOCKER_MESSAGE: String =
            "Decision dispatch is blocked pending contract reconciliation (PR3-003 / PR3-025): " +
                "decide.schema.json (additionalProperties: false) forbids attribution/rationale/scope/evidence " +
                "and omits headSha/branch required for verified completion."
    }
}
