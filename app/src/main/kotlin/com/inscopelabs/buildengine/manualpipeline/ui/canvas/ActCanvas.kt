package com.inscopelabs.buildengine.manualpipeline.ui.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inscopelabs.buildengine.manualpipeline.ui.act.ActStateMachine
import com.inscopelabs.buildengine.manualpipeline.ui.act.confirming.ActConfirmingComposable
import com.inscopelabs.buildengine.manualpipeline.ui.act.executing.ActExecutingComposable
import com.inscopelabs.buildengine.manualpipeline.ui.act.idle.ActIdleComposable
import com.inscopelabs.buildengine.manualpipeline.ui.act.outcome.ActOutcomeComposable
import com.inscopelabs.buildengine.manualpipeline.ui.act.verifying.ActVerifyingComposable

@Composable
fun ActCanvas(
    phase: ActStateMachine.Phase,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("mp_act_canvas"),
    ) {
        when (phase) {
            is ActStateMachine.Phase.Idle -> ActIdleComposable()
            is ActStateMachine.Phase.Confirming -> ActConfirmingComposable(state = phase.state)
            is ActStateMachine.Phase.Executing -> ActExecutingComposable(state = phase.state)
            is ActStateMachine.Phase.Verifying -> ActVerifyingComposable(state = phase.state)
            is ActStateMachine.Phase.Outcome -> ActOutcomeComposable(state = phase.state)
        }
    }
}
