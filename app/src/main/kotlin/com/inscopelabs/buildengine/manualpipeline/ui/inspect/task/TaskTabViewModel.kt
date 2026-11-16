package com.inscopelabs.buildengine.manualpipeline.ui.inspect.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.error.FailureCategoryMapper
import com.inscopelabs.buildengine.manualpipeline.domain.model.EligibilityVerdict
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.BuildLifecycleDashboardUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.DiscoverEligibleTasksUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.EvaluateEligibilityUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.InterpretWorkOrderUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ResolveAuthorityUseCase
import com.inscopelabs.buildengine.manualpipeline.ui.state.CanvasMode
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import com.inscopelabs.buildengine.manualpipeline.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TaskTabViewModel @Inject constructor(
    private val discoverEligibleTasksUseCase: DiscoverEligibleTasksUseCase,
    private val evaluateEligibilityUseCase: EvaluateEligibilityUseCase,
    private val buildLifecycleDashboardUseCase: BuildLifecycleDashboardUseCase,
    private val resolveAuthorityUseCase: ResolveAuthorityUseCase,
    private val interpretWorkOrderUseCase: InterpretWorkOrderUseCase,
    private val session: ManualPipelineSession,
) : ViewModel() {

    data class TaskTabData(
        val tasks: List<ManualTask>,
        val selectedTask: ManualTask?,
        val verdict: EligibilityVerdict?,
        val dashboard: BuildLifecycleDashboardUseCase.LifecycleDashboard,
        val authority: ResolveAuthorityUseCase.AuthorityReport,
    )

    private val _uiState = MutableStateFlow<UiState<TaskTabData>>(UiState.Idle)
    val uiState: StateFlow<UiState<TaskTabData>> = _uiState.asStateFlow()

    val sessionState = session.state

    fun load(preferredTaskId: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val authority = resolveAuthorityUseCase()
            val discovery = discoverEligibleTasksUseCase().getOrElse { err ->
                _uiState.value = UiState.Error(
                    message = err.message ?: "Failed to load tasks from Gateway A.",
                    category = FailureCategoryMapper.fromThrowable(err),
                )
                return@launch
            }

            val targetId = preferredTaskId?.takeIf { it.isNotBlank() }
                ?: session.state.value.selectedTask?.id
            val selected = discovery.tasks.firstOrNull { it.id == targetId }
                ?: discovery.tasks.firstOrNull()

            if (selected != null) {
                session.selectTask(selected, interpretWorkOrderUseCase(selected))
            }

            val verdict = selected?.let {
                evaluateEligibilityUseCase(
                    task = it,
                    authority = authority.snapshot,
                    liveBranchHead = it.headSha,
                )
            }
            val dashboard = buildLifecycleDashboardUseCase(discovery.tasks)

            _uiState.value = UiState.Success(
                TaskTabData(
                    tasks = discovery.tasks,
                    selectedTask = selected,
                    verdict = verdict,
                    dashboard = dashboard,
                    authority = authority,
                ),
            )
        }
    }

    fun selectTask(task: ManualTask) {
        val current = (_uiState.value as? UiState.Success)?.data ?: return
        session.selectTask(task, interpretWorkOrderUseCase(task))
        val verdict = evaluateEligibilityUseCase(
            task = task,
            authority = current.authority.snapshot,
            liveBranchHead = task.headSha,
        )
        _uiState.value = UiState.Success(
            current.copy(
                selectedTask = task,
                verdict = verdict,
            ),
        )
    }

    fun switchToActCanvas() {
        session.setCanvasMode(CanvasMode.ACT)
    }
}
