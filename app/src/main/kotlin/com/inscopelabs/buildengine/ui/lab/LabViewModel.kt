package com.inscopelabs.buildengine.ui.lab

import androidx.lifecycle.ViewModel
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lab screen state and scenario orchestration.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 * TODO(Stage 5.3 / T5-301): hold the scenario list and run state; refuse to run against any gateway that is not a test environment (LabEnvironmentGuard).
 *
 * Pre-execution scaffold (docs/PRE-EXECUTION-ONBOARDING.md). Not wired into navigation, DI or any caller.
 */
@HiltViewModel
class LabViewModel @Inject constructor() : ViewModel() {

    val uiState: StateFlow<UiState<Unit>> = MutableStateFlow(UiState.Idle)
}
