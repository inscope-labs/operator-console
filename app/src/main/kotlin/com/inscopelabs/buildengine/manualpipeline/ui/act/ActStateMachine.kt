package com.inscopelabs.buildengine.manualpipeline.ui.act

import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActConfirmingState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActExecutingState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActIdleState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActOutcomeState
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActVerifyingState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Finite state machine governing the operator Act canvas lifecycle:
 * Idle -> Confirming -> Executing -> Verifying -> Outcome.
 */
@Singleton
class ActStateMachine @Inject constructor() {

    sealed interface Phase {
        data class Idle(val state: ActIdleState = ActIdleState()) : Phase
        data class Confirming(val state: ActConfirmingState) : Phase
        data class Executing(val state: ActExecutingState) : Phase
        data class Verifying(val state: ActVerifyingState) : Phase
        data class Outcome(val state: ActOutcomeState) : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle())
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    fun updateIdle(idleState: ActIdleState) {
        if (_phase.value is Phase.Idle) {
            _phase.value = Phase.Idle(idleState)
        }
    }

    fun enterConfirming(state: ActConfirmingState) {
        _phase.value = Phase.Confirming(state)
    }

    fun updateConfirming(transform: (ActConfirmingState) -> ActConfirmingState) {
        val current = _phase.value as? Phase.Confirming ?: return
        _phase.value = Phase.Confirming(transform(current.state))
    }

    fun enterExecuting(state: ActExecutingState) {
        _phase.value = Phase.Executing(state)
    }

    fun updateExecuting(transform: (ActExecutingState) -> ActExecutingState) {
        val current = _phase.value as? Phase.Executing ?: return
        _phase.value = Phase.Executing(transform(current.state))
    }

    fun enterVerifying(state: ActVerifyingState) {
        _phase.value = Phase.Verifying(state)
    }

    fun updateVerifying(transform: (ActVerifyingState) -> ActVerifyingState) {
        val current = _phase.value as? Phase.Verifying ?: return
        _phase.value = Phase.Verifying(transform(current.state))
    }

    fun enterOutcome(state: ActOutcomeState) {
        _phase.value = Phase.Outcome(state)
    }

    fun resetToIdle() {
        _phase.value = Phase.Idle()
    }
}
