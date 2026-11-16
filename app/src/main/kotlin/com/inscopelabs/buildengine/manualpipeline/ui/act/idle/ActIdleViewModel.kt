package com.inscopelabs.buildengine.manualpipeline.ui.act.idle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ResolveAuthorityUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ResolveConfirmationTierUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.validation.TransitionPreconditionValidator
import com.inscopelabs.buildengine.manualpipeline.ui.act.ActStateMachine
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActActionKind
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActConfirmingState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActIdleState
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import com.inscopelabs.buildengine.manualpipeline.util.HeadHex
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ActIdleViewModel @Inject constructor(
    private val resolveAuthorityUseCase: ResolveAuthorityUseCase,
    private val resolveConfirmationTierUseCase: ResolveConfirmationTierUseCase,
    private val transitionPreconditionValidator: TransitionPreconditionValidator,
    private val session: ManualPipelineSession,
    private val stateMachine: ActStateMachine,
) : ViewModel() {

    private val _idleState = MutableStateFlow(ActIdleState())
    val idleState: StateFlow<ActIdleState> = _idleState.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { snap ->
                val task = snap.selectedTask
                val authorityReport = resolveAuthorityUseCase()
                val permitted = if (task != null) {
                    TransitionKind.entries.filter { kind ->
                        transitionPreconditionValidator.validate(task, kind).permitted
                    }
                } else {
                    emptyList()
                }
                val canTransition = authorityReport.canAct && permitted.isNotEmpty()
                val canUpload = authorityReport.canAct &&
                    task != null &&
                    task.hasOrderId &&
                    !task.isPlanHalted &&
                    snap.preparedArchive != null &&
                    snap.manifest != null &&
                    snap.sealedUploadSha256 == null

                val reason = when {
                    task == null -> "Select a task in Inspect Canvas first."
                    !authorityReport.canAct -> authorityReport.blockingReason
                    task.isPlanHalted -> "Plan is halted; privileged actions locked."
                    !task.hasOrderId -> "Work-order identifier unavailable from Gateway A."
                    else -> null
                }

                val next = ActIdleState(
                    selectedTask = task,
                    manifest = snap.manifest,
                    authority = authorityReport.snapshot,
                    canTransition = canTransition,
                    canUpload = canUpload,
                    permittedTransitions = permitted,
                    readOnlyReason = reason,
                )
                _idleState.value = next
                stateMachine.updateIdle(next)
            }
        }
    }

    fun startTransition(kind: TransitionKind) {
        val task = session.state.value.selectedTask ?: return
        viewModelScope.launch {
            val tierResolution = resolveConfirmationTierUseCase(kind.name)
            if (!tierResolution.allowed) {
                return@launch
            }
            stateMachine.enterConfirming(
                ActConfirmingState(
                    actionKind = ActActionKind.TRANSITION,
                    task = task,
                    transitionKind = kind,
                    confirmationTier = tierResolution.tier,
                    hardwareAvailable = tierResolution.hardwareAvailable,
                    tierAllowed = tierResolution.allowed,
                    tierReason = tierResolution.reason,
                    expectedEnvironment = tierResolution.authority.expectedEnvironment,
                    reportedEnvironment = tierResolution.authority.reportedEnvironment,
                    requiredHeadPrefix = HeadHex.first7(task.headSha),
                ),
            )
        }
    }

    fun startUpload() {
        val snap = session.state.value
        val task = snap.selectedTask ?: return
        val manifest = snap.manifest ?: return
        viewModelScope.launch {
            val tierResolution = resolveConfirmationTierUseCase("UPLOAD")
            if (!tierResolution.allowed) {
                return@launch
            }
            stateMachine.enterConfirming(
                ActConfirmingState(
                    actionKind = ActActionKind.UPLOAD,
                    task = task,
                    manifest = manifest,
                    confirmationTier = tierResolution.tier,
                    hardwareAvailable = tierResolution.hardwareAvailable,
                    tierAllowed = tierResolution.allowed,
                    tierReason = tierResolution.reason,
                    expectedEnvironment = tierResolution.authority.expectedEnvironment,
                    reportedEnvironment = tierResolution.authority.reportedEnvironment,
                    requiredHeadPrefix = HeadHex.first7(task.headSha),
                ),
            )
        }
    }
}
