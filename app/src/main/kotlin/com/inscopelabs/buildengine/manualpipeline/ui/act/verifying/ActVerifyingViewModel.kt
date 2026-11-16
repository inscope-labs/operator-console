package com.inscopelabs.buildengine.manualpipeline.ui.act.verifying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.VerifyTransitionUseCase
import com.inscopelabs.buildengine.manualpipeline.ui.act.ActStateMachine
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActOutcomeState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActVerifyingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ActVerifyingViewModel @Inject constructor(
    private val verifyTransitionUseCase: VerifyTransitionUseCase,
    private val stateMachine: ActStateMachine,
) : ViewModel() {

    fun runVerification(state: ActVerifyingState) {
        if (!state.isVerifying && state.verificationReport != null) return
        viewModelScope.launch {
            val report = verifyTransitionUseCase(
                planId = state.planId,
                taskId = state.taskId,
                branch = state.branch,
                expectedHeadSha = state.expectedHeadSha,
                auditRef = state.auditRef,
            )
            stateMachine.updateVerifying {
                it.copy(
                    isVerifying = false,
                    verificationReport = report,
                )
            }
        }
    }

    fun proceedToOutcome(state: ActVerifyingState) {
        stateMachine.enterOutcome(
            ActOutcomeState(
                actionKind = state.actionKind,
                taskId = state.taskId,
                outcome = state.outcome,
                verificationReport = state.verificationReport,
            ),
        )
    }
}
