package com.inscopelabs.buildengine.ui.decision

import androidx.lifecycle.ViewModel
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Decision state. Design review §12: a decision carries attribution and a permanent record.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
@HiltViewModel
class DecisionViewModel @Inject constructor() : ViewModel() {

    val uiState: StateFlow<UiState<Unit>> = MutableStateFlow(UiState.Idle)
}
