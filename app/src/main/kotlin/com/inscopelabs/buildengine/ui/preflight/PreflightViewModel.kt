package com.inscopelabs.buildengine.ui.preflight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.domain.model.preflight.PreflightReport
import com.inscopelabs.buildengine.domain.usecase.preflight.RunPreflightUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreflightViewModel @Inject constructor(
    private val runPreflightUseCase: RunPreflightUseCase,
) : ViewModel() {

    private val _report = MutableStateFlow(PreflightReport())
    val report: StateFlow<PreflightReport> = _report.asStateFlow()

    init {
        runChecks()
    }

    fun runChecks() {
        viewModelScope.launch {
            _report.value = _report.value.copy(isRunning = true)
            try {
                val result = runPreflightUseCase()
                _report.value = result
            } catch (t: Throwable) {
                _report.value = _report.value.copy(
                    isRunning = false,
                    overallPass = false,
                )
            }
        }
    }
}
