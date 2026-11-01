package com.inscopelabs.buildengine.ui.taskdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskDetailData(
    val task: Task,
    val branch: TaskBranch? = null,
)

/**
 * Task detail state holder.
 *
 * Loads task specifications and retrieves the live branch head for operator review.
 */
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<TaskDetailData>>(UiState.Idle)
    val uiState: StateFlow<UiState<TaskDetailData>> = _uiState.asStateFlow()

    fun loadTask(taskId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            when (val taskResult = taskRepository.getTask(taskId)) {
                is AppResult.Failure -> {
                    _uiState.value = UiState.Error(taskResult.message)
                }
                is AppResult.Success -> {
                    val task = taskResult.value
                    val branchResult = taskRepository.branchFor(taskId)
                    val branch = (branchResult as? AppResult.Success)?.value

                    _uiState.value = UiState.Success(
                        TaskDetailData(
                            task = task,
                            branch = branch,
                        ),
                    )
                }
            }
        }
    }
}
