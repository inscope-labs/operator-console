package com.inscopelabs.buildengine.manualpipeline.ui.act.outcome

import androidx.lifecycle.ViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.act.ActStateMachine
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ActOutcomeViewModel @Inject constructor(
    private val stateMachine: ActStateMachine,
    private val session: ManualPipelineSession,
) : ViewModel() {

    fun done() {
        stateMachine.resetToIdle()
    }

    fun openRecovery() {
        session.openRecovery(true)
    }

    fun openHandoff() {
        session.openHandoff(true)
    }
}
