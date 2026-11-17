package com.inscopelabs.buildengine.manualpipeline.ui.act.confirming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.error.ErrorPresentation
import com.inscopelabs.buildengine.manualpipeline.domain.error.FailureCategoryMapper
import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.PresentConcurrencyGuardUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.RequestTransitionUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.SubmitUploadUseCase
import com.inscopelabs.buildengine.manualpipeline.ui.act.ActStateMachine
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActActionKind
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActExecutingState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActOutcomeState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActVerifyingState
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ActConfirmingViewModel @Inject constructor(
    private val stateMachine: ActStateMachine,
    private val session: ManualPipelineSession,
    private val requestTransitionUseCase: RequestTransitionUseCase,
    private val submitUploadUseCase: SubmitUploadUseCase,
    private val presentConcurrencyGuardUseCase: PresentConcurrencyGuardUseCase,
) : ViewModel() {

    fun updateTypedHead(value: String) {
        stateMachine.updateConfirming { it.copy(typedHeadInput = value, validationError = null) }
    }

    fun cancelConfirmation() {
        stateMachine.resetToIdle()
    }

    fun confirmAndExecute() {
        val current = (stateMachine.phase.value as? ActStateMachine.Phase.Confirming)?.state ?: return
        val typed = current.typedHeadInput.trim()

        if (current.confirmationTier == ConfirmationTier.IN_APP_CONFIRM &&
            !typed.equals(current.requiredHeadPrefix, ignoreCase = true)
        ) {
            stateMachine.updateConfirming {
                it.copy(validationError = "Type the first ${current.requiredHeadPrefix.length} head characters (${current.requiredHeadPrefix}) to confirm.")
            }
            return
        }

        val snap = session.state.value
        val archive = snap.preparedArchive
        val manifest = current.manifest ?: snap.manifest
        val typedArg = if (current.confirmationTier == ConfirmationTier.IN_APP_CONFIRM) typed else null

        stateMachine.enterExecuting(
            ActExecutingState(
                actionKind = current.actionKind,
                taskId = current.task.id,
                planId = current.task.planId,
                branch = current.task.branch,
                headSha = current.task.headSha,
                stageLabel = if (current.actionKind == ActActionKind.UPLOAD) {
                    "Confirming capability & uploading immutable submission .zip..."
                } else {
                    "Confirming capability & dispatching ${current.transitionKind?.name ?: "transition"}..."
                },
                bytesTotal = manifest?.digest?.totalSizeBytes,
            ),
        )

        viewModelScope.launch {
            when (current.actionKind) {
                ActActionKind.TRANSITION -> {
                    val kind = current.transitionKind ?: TransitionKind.SET_IN_PROGRESS
                    val target = when (kind) {
                        TransitionKind.SET_IN_PROGRESS -> TaskStatus.IN_PROGRESS
                        TransitionKind.SET_DONE -> TaskStatus.COMPLETED
                    }
                    val req = TransitionRequest(
                        planId = current.task.planId,
                        taskId = current.task.id,
                        orderId = current.task.orderId,
                        branch = current.task.branch,
                        expectedHead = current.task.headSha,
                        kind = kind,
                        currentStatus = current.task.status,
                        targetStatus = target,
                        typedHeadConfirmation = typedArg,
                    )
                    when (val res = requestTransitionUseCase(current.task, req)) {
                        is RequestTransitionUseCase.TransitionResult.MintBlocked -> {
                            stateMachine.enterOutcome(
                                ActOutcomeState(
                                    actionKind = ActActionKind.TRANSITION,
                                    taskId = current.task.id,
                                    outcome = OperationOutcome.Refused(res.code, res.reason),
                                    guardRefusal = presentConcurrencyGuardUseCase.fromCode(res.code, res.reason),
                                    errorPresentation = ErrorPresentation.from(
                                        FailureCategoryMapper.fromGatewayCode(res.code, res.reason),
                                        res.code,
                                        res.reason,
                                    ),
                                    blockedCode = res.code,
                                    blockedReason = res.reason,
                                ),
                            )
                        }
                        is RequestTransitionUseCase.TransitionResult.Executed -> {
                            handleOutcome(
                                kind = ActActionKind.TRANSITION,
                                task = current.task,
                                targetStatus = target,
                                outcome = res.outcome,
                                verifiedHead = res.verifiedLiveHeadSha,
                                uploadedSha = null,
                            )
                        }
                    }
                }
                ActActionKind.UPLOAD -> {
                    if (archive == null || manifest == null) {
                        stateMachine.enterOutcome(
                            ActOutcomeState(
                                actionKind = ActActionKind.UPLOAD,
                                taskId = current.task.id,
                                outcome = OperationOutcome.Refused("ARCHIVE_EMPTY", "No prepared archive found."),
                                blockedCode = "ARCHIVE_EMPTY",
                                blockedReason = "Attach a submission .zip in the Artifacts tab first.",
                            ),
                        )
                        return@launch
                    }
                    when (
                        val res = submitUploadUseCase(
                            task = current.task,
                            manifest = manifest,
                            zipBase64 = archive.zipBase64,
                            typedHeadConfirmation = typedArg,
                            alreadyUploadedSha256 = snap.sealedUploadSha256,
                        )
                    ) {
                        is SubmitUploadUseCase.UploadResult.Blocked -> {
                            stateMachine.enterOutcome(
                                ActOutcomeState(
                                    actionKind = ActActionKind.UPLOAD,
                                    taskId = current.task.id,
                                    outcome = OperationOutcome.Refused(res.code, res.reason),
                                    guardRefusal = presentConcurrencyGuardUseCase.fromCode(res.code, res.reason),
                                    errorPresentation = ErrorPresentation.from(
                                        FailureCategoryMapper.fromGatewayCode(res.code, res.reason),
                                        res.code,
                                        res.reason,
                                    ),
                                    blockedCode = res.code,
                                    blockedReason = res.reason,
                                ),
                            )
                        }
                        is SubmitUploadUseCase.UploadResult.Executed -> {
                            handleOutcome(
                                kind = ActActionKind.UPLOAD,
                                task = current.task,
                                targetStatus = null,
                                outcome = res.outcome,
                                verifiedHead = res.verifiedHeadSha,
                                uploadedSha = manifest.digest.zipSha256,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleOutcome(
        kind: ActActionKind,
        task: com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask,
        targetStatus: TaskStatus?,
        outcome: OperationOutcome,
        verifiedHead: String,
        uploadedSha: String?,
    ) {
        if (outcome is OperationOutcome.Succeeded) {
            if (kind == ActActionKind.UPLOAD && !uploadedSha.isNullOrBlank()) {
                session.recordUploadEvidence(outcome.auditRef, "", uploadedSha)
            } else {
                session.recordTransitionEvidence(outcome.auditRef, "")
                if (targetStatus != null) {
                    session.selectTask(task.copy(status = targetStatus, headSha = outcome.headSha.ifBlank { verifiedHead }))
                }
            }
            stateMachine.enterVerifying(
                ActVerifyingState(
                    actionKind = kind,
                    taskId = task.id,
                    planId = task.planId,
                    branch = task.branch,
                    expectedHeadSha = outcome.headSha.ifBlank { verifiedHead },
                    outcome = outcome,
                    auditRef = outcome.auditRef,
                    isVerifying = true,
                ),
            )
        } else {
            val guard = presentConcurrencyGuardUseCase.fromOutcome(outcome)
            val errPres = when (outcome) {
                is OperationOutcome.Refused -> ErrorPresentation.from(FailureCategoryMapper.fromGatewayCode(outcome.code, outcome.message), outcome.code, outcome.message)
                is OperationOutcome.Conflict -> ErrorPresentation.from(FailureCategoryMapper.fromGatewayCode(outcome.code, outcome.message), outcome.code, outcome.message)
                is OperationOutcome.Timeout -> ErrorPresentation.from(FailureCategoryMapper.fromGatewayCode("TIMEOUT", outcome.message), "TIMEOUT", outcome.message)
                is OperationOutcome.UncertainCompletion -> ErrorPresentation.from(FailureCategoryMapper.fromGatewayCode("RECONCILE_REQUIRED", outcome.message), "UNCERTAIN", outcome.message)
                is OperationOutcome.RecoveryRequired -> ErrorPresentation.from(FailureCategoryMapper.fromGatewayCode(outcome.code, outcome.message), outcome.code, outcome.message)
                is OperationOutcome.Succeeded -> null
            }
            stateMachine.enterOutcome(
                ActOutcomeState(
                    actionKind = kind,
                    taskId = task.id,
                    outcome = outcome,
                    guardRefusal = guard,
                    errorPresentation = errPres,
                ),
            )
        }
    }
}
