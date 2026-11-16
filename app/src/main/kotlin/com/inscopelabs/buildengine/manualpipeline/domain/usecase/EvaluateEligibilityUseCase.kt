package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.EligibilityCheck
import com.inscopelabs.buildengine.manualpipeline.domain.model.EligibilityVerdict
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthoritySnapshot
import com.inscopelabs.buildengine.manualpipeline.domain.validation.EnvironmentIsolationValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import javax.inject.Inject

/**
 * MP-002: Evaluates local advisory eligibility checks for a discovered task.
 * Strictly advisory only (I-02) — Gateway A enforces authoritative guards on every write.
 */
class EvaluateEligibilityUseCase @Inject constructor(
    private val headFreshnessValidator: HeadFreshnessValidator,
    private val environmentIsolationValidator: EnvironmentIsolationValidator,
) {

    operator fun invoke(
        task: ManualTask,
        authority: AuthoritySnapshot,
        liveBranchHead: String? = null,
    ): EligibilityVerdict {
        val checks = mutableListOf<EligibilityCheck>()

        val envResult = environmentIsolationValidator.validate(authority)
        checks += EligibilityCheck(
            name = "Environment parity",
            passed = envResult.matched,
            reason = envResult.reason,
        )

        checks += EligibilityCheck(
            name = "Device enrolment & credential",
            passed = authority.enrolled && authority.hasOperatorCredential,
            reason = when {
                !authority.hasOperatorCredential -> "Operator bearer credential is not configured."
                !authority.enrolled -> "Device key is not enrolled with Gateway A."
                else -> "Enrolled key '${authority.activeKid ?: "active"}' with ${authority.confirmationTier?.wireValue ?: "unknown"} tier."
            },
        )

        checks += EligibilityCheck(
            name = "Halt & block state",
            passed = !task.isPlanHalted && !task.isBlockedByGateway,
            reason = when {
                task.isPlanHalted -> "Plan is halted; write transitions disabled."
                task.isBlockedByGateway -> "Task is listed in gateway blockedTasks."
                else -> "No plan halt or gateway block reported."
            },
        )

        checks += EligibilityCheck(
            name = "Work-order binding",
            passed = task.hasOrderId,
            reason = if (task.hasOrderId) {
                "Gateway work-order '${task.orderId}' bound."
            } else {
                "No gateway work-order identifier available; write actions disabled."
            },
        )

        val headCheck = if (liveBranchHead != null) {
            when (val r = headFreshnessValidator.validate(task.headSha, liveBranchHead)) {
                is HeadFreshnessValidator.Result.Fresh -> EligibilityCheck(
                    name = "Branch head freshness",
                    passed = true,
                    reason = "Matches live 40-char head (${r.verifiedHeadSha.take(7)}…).",
                )
                is HeadFreshnessValidator.Result.Drifted -> EligibilityCheck(
                    name = "Branch head freshness",
                    passed = false,
                    reason = "Head drifted from ${r.expectedSha.take(7)} to ${r.liveSha.take(7)}.",
                )
                is HeadFreshnessValidator.Result.InvalidFormat -> EligibilityCheck(
                    name = "Branch head freshness",
                    passed = false,
                    reason = "Head SHA is not a full 40-char hex commit.",
                )
            }
        } else {
            EligibilityCheck(
                name = "Branch head format",
                passed = headFreshnessValidator.isFullSha(task.headSha),
                reason = if (headFreshnessValidator.isFullSha(task.headSha)) {
                    "Full 40-char head SHA present."
                } else {
                    "Full 40-char head SHA missing."
                },
            )
        }
        checks += headCheck

        val statusEligible = task.status == TaskStatus.READY || task.status == TaskStatus.IN_PROGRESS
        checks += EligibilityCheck(
            name = "Gateway task status",
            passed = statusEligible,
            reason = "Current status is '${task.status.wireValue}' (advisory check).",
        )

        val advisoryReady = checks.all { it.passed }
        val summary = if (advisoryReady) {
            "Advisory checks passed. Gateway A guards remain authoritative on submit."
        } else {
            checks.first { !it.passed }.reason
        }

        return EligibilityVerdict(
            taskId = task.id,
            advisoryReady = advisoryReady,
            checks = checks,
            summaryReason = summary,
        )
    }
}
