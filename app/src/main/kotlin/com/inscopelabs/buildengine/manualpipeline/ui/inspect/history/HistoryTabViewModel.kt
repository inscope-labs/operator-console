package com.inscopelabs.buildengine.manualpipeline.ui.inspect.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.data.repository.AuditRepositoryImpl
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperatorActionRecord
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditEntry
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperatorActionStore
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryTabViewModel @Inject constructor(
    private val operatorActionStore: OperatorActionStore,
    private val auditRepository: AuditRepositoryImpl,
    private val session: ManualPipelineSession,
) : ViewModel() {

    data class HistoryUiModel(
        val actions: List<OperatorActionRecord> = emptyList(),
        val auditEntries: List<AuditEntry> = emptyList(),
        val selectedAudit: AuditEntry? = null,
        val isLoadingAudit: Boolean = false,
        val auditError: String? = null,
    )

    private val _uiModel = MutableStateFlow(HistoryUiModel())
    val uiModel: StateFlow<HistoryUiModel> = _uiModel.asStateFlow()

    init {
        viewModelScope.launch {
            operatorActionStore.observeAllActions().collect { list ->
                _uiModel.update { it.copy(actions = list) }
            }
        }
        refreshAudit()
    }

    fun refreshAudit() {
        viewModelScope.launch {
            _uiModel.update { it.copy(isLoadingAudit = true, auditError = null) }
            auditRepository.fetchRecent(30).fold(
                onSuccess = { entries ->
                    val activeTask = session.state.value.selectedTask?.id
                    val sorted = if (activeTask.isNullOrBlank()) {
                        entries
                    } else {
                        entries.sortedByDescending { it.taskId == activeTask }
                    }
                    _uiModel.update { it.copy(auditEntries = sorted, isLoadingAudit = false) }
                },
                onFailure = { err ->
                    _uiModel.update {
                        it.copy(
                            isLoadingAudit = false,
                            auditError = err.message ?: "Unable to fetch GET /audit records.",
                        )
                    }
                },
            )
        }
    }

    fun inspectAuditRef(auditRef: String) {
        if (auditRef.isBlank()) return
        viewModelScope.launch {
            _uiModel.update { it.copy(isLoadingAudit = true, auditError = null) }
            auditRepository.fetchByRef(auditRef).fold(
                onSuccess = { entry ->
                    _uiModel.update { it.copy(selectedAudit = entry, isLoadingAudit = false) }
                },
                onFailure = { err ->
                    _uiModel.update {
                        it.copy(
                            isLoadingAudit = false,
                            auditError = err.message ?: "Audit entry '$auditRef' unavailable.",
                        )
                    }
                },
            )
        }
    }
}
