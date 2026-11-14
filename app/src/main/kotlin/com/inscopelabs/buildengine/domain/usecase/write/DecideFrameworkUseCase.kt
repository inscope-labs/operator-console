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
 * Target for a framework decision (`POST /plans/{id}/decide`, §3.18; T3-404, PR3-003, PR3-025).
 */
data class DecideTarget(
    val plan: String,
    val task: String,
    val branch: String,
    val headSha: String,
    val order: String,
    val decision: FrameworkDecision,
    val attribution: String,
    val rationale: String,
    val scope: String,
    val evidence: String,
    val notes: String? = null,
)

sealed interface DecideResult {
    data class Executed(val outcome: OperationOutcome) : DecideResult

    /**
     * Blocked before minting/sending because the gateway schema/route contract is unsettled
     * (`PR3-003` / `PR3-025`): `decide.schema.json` forbids the attribution/rationale/scope/evidence
     * fields and omits `headSha`/`branch` needed for verified completion (T3-302).
     */
    data class ContractBlocked(val reason: String) : DecideResult
    data class NotAuthorized(val reason: MintResult) : DecideResult
    data class InvalidTarget(val reason: String) : DecideResult
}

/**
 * Resolves a framework decision (`POST /plans/{id}/decide`, §3.18).
 *
 * Validates all required attribution and decision fields locally, then fails closed with
 * [DecideResult.ContractBlocked] while [contractSchemaSettled] is `false` (default, per PR3-003
 * and PR3-025) so the console never fabricates unapproved wire fields or bypasses schema checks.
 */
class DecideFrameworkUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
    private val nonceSource: NonceSource,
    private val contractSchemaSettled: Boolean = false,
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
            target.attribution.isBlank() ||
            target.rationale.isBlank() ||
            target.scope.isBlank() ||
            target.evidence.isBlank()
        ) {
            return DecideResult.InvalidTarget(
                "Plan, task, order, attribution, rationale, scope and evidence are required",
            )
        }
        val textInputs = listOfNotNull(
            target.attribution,
            target.rationale,
            target.scope,
            target.evidence,
            target.notes,
        )
        if (textInputs.any { Redactor.containsForbidden(it) }) {
            return DecideResult.InvalidTarget(
                "Decision inputs must not contain forbidden credential or signature patterns",
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
