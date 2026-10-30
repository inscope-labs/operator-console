package com.inscopelabs.buildengine.ui.plans

import androidx.lifecycle.ViewModel
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Plan list and plan detail state.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@HiltViewModel
class PlanViewModel @Inject constructor() : ViewModel() {

    val uiState: StateFlow<UiState<Unit>> = MutableStateFlow(UiState.Idle)
}
