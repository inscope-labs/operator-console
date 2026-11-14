package com.inscopelabs.buildengine.ui.publish

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.data.local.HealthSnapshotStore
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import com.inscopelabs.buildengine.domain.usecase.write.PublishOrderResolver
import com.inscopelabs.buildengine.domain.usecase.write.PublishResult
import com.inscopelabs.buildengine.domain.usecase.write.PublishTarget
import com.inscopelabs.buildengine.domain.usecase.write.PublishTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the confirmation screen shows before anything is signed (T3-301). */
data class PublishSummary(
    val taskId: String,
    val taskTitle: String,
    val plan: String,
    /** Work-order identifier, or null when the contract gives the console no way to obtain it. */
    val order: String?,
    val branch: String,
    /** The full 40-character head the operator is approving. Never abbreviated. */
    val headSha: String,
    val tier: AuthTier?,
    val environment: String?,
    val ttlSeconds: Long,
) {
    /** Publish needs a resolvable order and a known enrolment tier; otherwise it fails closed. */
    val canConfirm: Boolean get() = !order.isNullOrBlank() && tier != null

    /** The typed head prefix the in-app tier asks for (first 7 characters). */
    val typedHeadPrefix: String get() = headSha.take(TYPED_HEAD_LENGTH)

    companion object {
        const val TYPED_HEAD_LENGTH = 7
    }
}

sealed interface PublishUiState {
    data object Idle : PublishUiState
    data object Loading : PublishUiState
    data class LoadFailed(val message: String) : PublishUiState
    data class Ready(val summary: PublishSummary, val typedHead: String = "") : PublishUiState
    data class Submitting(val summary: PublishSummary) : PublishUiState
    data class Finished(val summary: PublishSummary, val banner: PublishBanner) : PublishUiState
}

/**
 * Publish flow state. The confirmation shows the branch, the exact head SHA and the guard
 * summary before anything is signed. The state machine admits exactly one submission per
 * confirmation; the journal-backed executor additionally refuses a second in-flight send.
 */
@HiltViewModel
class PublishViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val publishTask: PublishTaskUseCase,
    private val taskRepository: TaskRepository,
    private val orderResolver: PublishOrderResolver,
    private val enrollmentStore: DeviceEnrollmentStore,
    private val healthSnapshotStore: HealthSnapshotStore,
    private val capabilityMinter: CapabilityMinter,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PublishUiState>(PublishUiState.Idle)
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    private val taskId: String = savedStateHandle.get<String>("taskId").orEmpty()

    init {
        load()
    }

    fun load() {
        if (taskId.isBlank()) {
            _uiState.value = PublishUiState.LoadFailed("No task selected")
            return
        }
        _uiState.value = PublishUiState.Loading
        viewModelScope.launch {
            val task = when (val result = taskRepository.getTask(taskId)) {
                is AppResult.Failure -> {
                    _uiState.value = PublishUiState.LoadFailed(result.message)
                    return@launch
                }
                is AppResult.Success -> result.value
            }
            // The live branch head is authoritative; the cached task head is never approved.
            val branch = when (val result = taskRepository.branchFor(taskId)) {
                is AppResult.Failure -> {
                    _uiState.value = PublishUiState.LoadFailed(result.message)
                    return@launch
                }
                is AppResult.Success -> result.value
            }
            val tier = enrollmentStore.activeRecord()?.authTier?.let { AuthTier.fromWireValue(it) }
            _uiState.value = PublishUiState.Ready(
                PublishSummary(
                    taskId = task.id,
                    taskTitle = task.title,
                    plan = task.planId,
                    order = orderResolver.resolve(task),
                    branch = branch.name,
                    headSha = branch.headSha,
                    tier = tier,
                    environment = healthSnapshotStore.read()?.environment,
                    ttlSeconds = capabilityMinter.ttlSeconds,
                ),
            )
        }
    }

    fun onTypedHeadChanged(text: String) {
        val current = _uiState.value as? PublishUiState.Ready ?: return
        _uiState.value = current.copy(typedHead = text)
    }

    /** Confirms and sends. Ignored unless the state is [PublishUiState.Ready] and publishable. */
    fun confirm() {
        val ready = _uiState.value as? PublishUiState.Ready ?: return
        val summary = ready.summary
        val order = summary.order
        if (!summary.canConfirm || order.isNullOrBlank()) return
        _uiState.value = PublishUiState.Submitting(summary)
        viewModelScope.launch {
            val result: PublishResult = publishTask(
                target = PublishTarget(
                    taskId = summary.taskId,
                    plan = summary.plan,
                    order = order,
                    branch = summary.branch,
                    headSha = summary.headSha,
                ),
                typedHeadConfirmation = ready.typedHead.takeIf { it.isNotBlank() },
            )
            _uiState.value = PublishUiState.Finished(summary, PublishBannerPresenter.present(result))
        }
    }
}
