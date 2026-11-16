package com.inscopelabs.buildengine.manualpipeline.ui.act.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ReconcileOperationUseCase
import com.inscopelabs.buildengine.manualpipeline.recovery.RecoveryReconciler
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val reconcileOperationUseCase: ReconcileOperationUseCase,
    private val recoveryReconciler: RecoveryReconciler,
    private val session: ManualPipelineSession,
) : ViewModel() {

    data class RecoveryUiModel(
        val pendingRecords: List<OperationRecord> = emptyList(),
        val statusesByKey: Map<String, RecoveryStatus> = emptyMap(),
        val isReconciling: Boolean = false,
        val lastSummary: String? = null,
    )

    private val _uiModel = MutableStateFlow(RecoveryUiModel())
    val uiModel: StateFlow<RecoveryUiModel> = _uiModel.asStateFlow()

    init {
        refreshQueue()
    }

    fun refreshQueue() {
        viewModelScope.launch {
            val pending = reconcileOperationUseCase.loadRecoveryQueue()
            _uiModel.update { it.copy(pendingRecords = pending) }
        }
    }

    fun reconcileSingle(key: String) {
        viewModelScope.launch {
            _uiModel.update { it.copy(isReconciling = true) }
            val result = recoveryReconciler.reconcileSingle(key)
            val refreshed = reconcileOperationUseCase.loadRecoveryQueue()
            _uiModel.update { prev ->
                prev.copy(
                    pendingRecords = refreshed,
                    statusesByKey = prev.statusesByKey + (key to result),
                    isReconciling = false,
                    lastSummary = "Lookup $key -> ${result.state.name}",
                )
            }
        }
    }

    fun reconcileAll() {
        viewModelScope.launch {
            _uiModel.update { it.copy(isReconciling = true) }
            val summary = recoveryReconciler.reconcileUncertain()
            val refreshed = reconcileOperationUseCase.loadRecoveryQueue()
            _uiModel.update {
                it.copy(
                    pendingRecords = refreshed,
                    isReconciling = false,
                    lastSummary = "Checked ${summary.totalChecked} intent(s); ${summary.stillPendingCount} still pending.",
                )
            }
        }
    }

    fun close() {
        session.openRecovery(false)
    }
}
