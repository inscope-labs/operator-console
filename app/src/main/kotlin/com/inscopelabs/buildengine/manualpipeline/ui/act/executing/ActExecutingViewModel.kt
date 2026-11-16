package com.inscopelabs.buildengine.manualpipeline.ui.act.executing

import androidx.lifecycle.ViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.act.ActStateMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ActExecutingViewModel @Inject constructor(
    private val stateMachine: ActStateMachine,
) : ViewModel() {
    val phase = stateMachine.phase
}
