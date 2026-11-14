package com.inscopelabs.buildengine.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.DoctorReport
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.SyncStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.usecase.read.GetDoctorReportUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetNextTasksUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.read.SyncBranchUseCase
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardData(
    val plans: List<Plan> = emptyList(),
    val nextTasks: List<Task> = emptyList(),
    val planStatus: PlanStatus? = null,
    val isHalted: Boolean = false,
    val openBranches: List<String> = emptyList(),
)

/**
 * State for user-triggered `POST /sync` and `GET /doctor` diagnostics on the Dashboard (T3-405).
 */
data class DashboardDiagnosticsState(
    val isSyncing: Boolean = false,
    val syncStatus: SyncStatus? = null,
    val syncError: String? = null,
    val isRunningDoctor: Boolean = false,
    val doctorReport: DoctorReport? = null,
    val doctorError: String? = null,
)

/**
 * Dashboard state holder.
 *
 * Reads plans, next actionable tasks, system status, sync status, and doctor diagnostics.
 * Does not load in [init]: network access is deliberate (refresh / retry / sync / doctor).
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getPlansUseCase: GetPlansUseCase,
    private val getNextTasksUseCase: GetNextTasksUseCase,
    private val getStatusUseCase: GetStatusUseCase,
    private val syncBranchUseCase: SyncBranchUseCase,
    private val getDoctorReportUseCase: GetDoctorReportUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DashboardData>>(UiState.Idle)
    val uiState: StateFlow<UiState<DashboardData>> = _uiState.asStateFlow()

    private val _diagnosticsState = MutableStateFlow(DashboardDiagnosticsState())
    val diagnosticsState: StateFlow<DashboardDiagnosticsState> = _diagnosticsState.asStateFlow()

    fun refresh() {
        loadDashboard()
    }

    fun syncBranch(branch: String = "main") {
        if (_diagnosticsState.value.isSyncing) return
        viewModelScope.launch {
            _diagnosticsState.update { it.copy(isSyncing = true, syncError = null) }
            when (val result = syncBranchUseCase(branch)) {
                is AppResult.Success -> _diagnosticsState.update {
                    it.copy(isSyncing = false, syncStatus = result.value, syncError = null)
                }
                is AppResult.Failure -> _diagnosticsState.update {
                    it.copy(isSyncing = false, syncError = result.message)
                }
            }
        }
    }

    fun runDoctor() {
        if (_diagnosticsState.value.isRunningDoctor) return
        viewModelScope.launch {
            _diagnosticsState.update { it.copy(isRunningDoctor = true, doctorError = null) }
            when (val result = getDoctorReportUseCase()) {
                is AppResult.Success -> _diagnosticsState.update {
                    it.copy(isRunningDoctor = false, doctorReport = result.value, doctorError = null)
                }
                is AppResult.Failure -> _diagnosticsState.update {
                    it.copy(isRunningDoctor = false, doctorError = result.message)
                }
            }
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            when (val plansResult = getPlansUseCase()) {
                is AppResult.Failure -> {
                    _uiState.value = UiState.Error(plansResult.message)
                }
                is AppResult.Success -> {
                    val plans = plansResult.value
                    val nextTasks = when (val tasksResult = getNextTasksUseCase()) {
                        is AppResult.Success -> tasksResult.value
                        is AppResult.Failure -> emptyList()
                    }

                    var planStatus: PlanStatus? = null
                    var isHalted = false
                    var openBranches = emptyList<String>()

                    val primaryPlan = plans.firstOrNull()
                    if (primaryPlan != null) {
                        when (val statusResult = getStatusUseCase(primaryPlan.id)) {
                            is AppResult.Success -> {
                                planStatus = statusResult.value
                                isHalted = planStatus.halted
                                openBranches = planStatus.openBranches
                            }
                            is AppResult.Failure -> {
                                // Status failure does not hide the rest of the dashboard
                            }
                        }
                    }

                    _uiState.value = UiState.Success(
                        DashboardData(
                            plans = plans,
                            nextTasks = nextTasks,
                            planStatus = planStatus,
                            isHalted = isHalted,
                            openBranches = openBranches,
                        ),
                    )
                }
            }
        }
    }
}
