package com.inscopelabs.buildengine.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.usecase.read.GetNextTasksUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardData(
    val plans: List<Plan> = emptyList(),
    val nextTasks: List<Task> = emptyList(),
    val planStatus: PlanStatus? = null,
    val isHalted: Boolean = false,
    val openBranches: List<String> = emptyList(),
)

/**
 * Dashboard state holder.
 *
 * Reads plans, next actionable tasks, and system status.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getPlansUseCase: GetPlansUseCase,
    private val getNextTasksUseCase: GetNextTasksUseCase,
    private val getStatusUseCase: GetStatusUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DashboardData>>(UiState.Idle)
    val uiState: StateFlow<UiState<DashboardData>> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun refresh() {
        loadDashboard()
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
