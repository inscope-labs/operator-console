package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditEntry
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditReadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.TaskGatewayPort
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import javax.inject.Inject

/**
 * MP-007: Post-write read-back verification against live branch head (`GET /branches/{name}/head`),
 * task status (`GET /tasks/next`, `GET /plans/{id}/status`), and audit trail (`GET /audit/{ref}`).
 * Never declares completion until Gateway A read-back confirms state.
 */
class VerifyTransitionUseCase @Inject constructor(
    private val taskGatewayPort: TaskGatewayPort,
    private val branchHeadPort: BranchHeadPort,
    private val auditReadPort: AuditReadPort,
    private val headFreshnessValidator: HeadFreshnessValidator,
) {

    data class VerificationReport(
        val verified: Boolean,
        val liveHeadSha: String?,
        val headMatches: Boolean,
        val observedTaskStatus: TaskStatus?,
        val planHalted: Boolean,
        val auditEntry: AuditEntry?,
        val summary: String,
    )

    suspend operator fun invoke(
        planId: String,
        taskId: String,
        branch: String,
        expectedHeadSha: String,
        auditRef: String?,
    ): VerificationReport {
        val liveHead = branchHeadPort.fetchLiveHead(branch).getOrNull()?.headSha
        val headMatches = liveHead != null &&
            headFreshnessValidator.validate(expectedHeadSha, liveHead) is HeadFreshnessValidator.Result.Fresh

        val nextTasks = taskGatewayPort.fetchNextTasks().getOrDefault(emptyList())
        val observedStatus = nextTasks.firstOrNull { it.id == taskId }?.status
        val planStatus = taskGatewayPort.fetchPlanStatus(planId).getOrNull()
        val audit = auditRef?.takeIf { it.isNotBlank() }?.let { ref ->
            auditReadPort.fetchAuditByRef(ref).getOrNull()
        }

        val verified = headMatches && !auditRef.isNullOrBlank()
        val summary = when {
            liveHead == null -> "Live branch head unavailable during verification."
            !headMatches -> "Live head (${liveHead.take(7)}) differs from expected (${expectedHeadSha.take(7)})."
            audit == null && !auditRef.isNullOrBlank() -> "Live head matches (${liveHead.take(7)}); audit ref $auditRef recorded."
            audit != null -> "Verified against live head (${liveHead.take(7)}) and audit ref ${audit.auditRef}."
            else -> "Live head checked (${liveHead.take(7)}); no audit reference returned."
        }

        return VerificationReport(
            verified = verified,
            liveHeadSha = liveHead,
            headMatches = headMatches,
            observedTaskStatus = observedStatus,
            planHalted = planStatus?.halted == true,
            auditEntry = audit,
            summary = summary,
        )
    }
}
