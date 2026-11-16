package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.error.GatewayRefusal
import com.inscopelabs.buildengine.manualpipeline.domain.model.GuardRefusal
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import javax.inject.Inject

/**
 * MP-011: Translates gateway refusals & concurrency conflicts into lossless [GuardRefusal] UI
 * presentations without stripping codes, rewriting head SHAs, or masking seal/halt refusals (I-05).
 */
class PresentConcurrencyGuardUseCase @Inject constructor() {

    fun fromOutcome(outcome: OperationOutcome): GuardRefusal? = when (outcome) {
        is OperationOutcome.Refused -> {
            val ref = GatewayRefusal(code = outcome.code, message = outcome.message, auditRef = outcome.auditRef)
            GuardRefusal(
                code = outcome.code,
                message = outcome.message,
                guardName = inferGuardName(outcome.code),
                isSealRefusal = ref.isSealRefusal,
                isHaltRefusal = ref.isHaltRefusal,
            )
        }
        is OperationOutcome.Conflict -> {
            val ref = GatewayRefusal(code = outcome.code, message = outcome.message, auditRef = outcome.auditRef)
            GuardRefusal(
                code = outcome.code,
                message = outcome.message,
                guardName = inferGuardName(outcome.code),
                isSealRefusal = ref.isSealRefusal,
                isHaltRefusal = ref.isHaltRefusal,
            )
        }
        else -> null
    }

    fun fromCode(code: String, message: String): GuardRefusal {
        val ref = GatewayRefusal(code = code, message = message)
        return GuardRefusal(
            code = code,
            message = message,
            guardName = inferGuardName(code),
            isSealRefusal = ref.isSealRefusal,
            isHaltRefusal = ref.isHaltRefusal,
        )
    }

    private fun inferGuardName(code: String): String? = when (code.uppercase()) {
        "HEAD_DRIFT", "HEAD_MOVED" -> "branch_head_guard"
        "HALT_ACTIVE" -> "repository_halt_guard"
        "SUBMISSION_IMMUTABLE", "SEALED" -> "submission_seal_guard"
        "NONCE_REPLAYED" -> "capability_replay_guard"
        "SCOPE_REFUSED" -> "capability_scope_guard"
        "ANCESTRY_FAIL" -> "git_ancestry_guard"
        "RECONCILE_REQUIRED" -> "reconcile_guard"
        "ENVIRONMENT_MISMATCH" -> "environment_isolation_guard"
        else -> null
    }
}
