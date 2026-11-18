package com.inscopelabs.buildengine.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.data.enrollment.DeviceEnrollmentStore
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.usecase.common.MintResult
import com.inscopelabs.buildengine.domain.usecase.read.GetNextTasksUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.read.RenderCheckUseCase
import com.inscopelabs.buildengine.domain.usecase.read.ValidatePlanUseCase
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusResult
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusTarget
import com.inscopelabs.buildengine.domain.usecase.write.SetTaskStatusUseCase
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TaskTransitionFeedback(
    val taskId: String,
    val taskTitle: String,
    val accepted: Boolean,
    val message: String,
    val auditRef: String? = null,
    val errorCode: String? = null,
)

data class PlanDetailData(
    val plan: Plan,
    val status: PlanStatus? = null,
    val validation: PlanValidation? = null,
    val renderCheck: RenderCheck? = null,
    val isValidating: Boolean = false,
    val isRenderChecking: Boolean = false,
    val pendingTasks: List<Task> = emptyList(),
    val authTier: AuthTier? = null,
    val startingTaskId: String? = null,
    val transitionFeedback: TaskTransitionFeedback? = null,
)

/**
 * Plan list and plan detail state holder.
 *
 * Does not load the plan list in [init]: network access is deliberate
 * (explicit [loadPlans] / refresh / after connect).
 *
 * Per ADR 0016 D-1, only `SET_IN_PROGRESS` (`PENDING -> IN_PROGRESS`) is exposed.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val getPlansUseCase: GetPlansUseCase,
    private val planRepository: PlanRepository,
    private val getStatusUseCase: GetStatusUseCase,
    private val validatePlanUseCase: ValidatePlanUseCase,
    private val renderCheckUseCase: RenderCheckUseCase,
    private val getNextTasksUseCase: GetNextTasksUseCase,
    private val setTaskStatusUseCase: SetTaskStatusUseCase,
    private val enrollmentStore: DeviceEnrollmentStore,
) : ViewModel() {

    private val _planListState = MutableStateFlow<UiState<List<Plan>>>(UiState.Idle)
    val planListState: StateFlow<UiState<List<Plan>>> = _planListState.asStateFlow()

    private val _planDetailState = MutableStateFlow<UiState<PlanDetailData>>(UiState.Idle)
    val planDetailState: StateFlow<UiState<PlanDetailData>> = _planDetailState.asStateFlow()

    fun loadPlans() {
        viewModelScope.launch {
            _planListState.value = UiState.Loading
            when (val result = getPlansUseCase()) {
                is AppResult.Success -> _planListState.value = UiState.Success(result.value)
                is AppResult.Failure -> _planListState.value = UiState.Error(result.message)
            }
        }
    }

    fun loadPlanDetail(planId: String) {
        viewModelScope.launch {
            _planDetailState.value = UiState.Loading
            when (val planResult = planRepository.getPlan(planId)) {
                is AppResult.Failure -> {
                    _planDetailState.value = UiState.Error(planResult.message)
                }
                is AppResult.Success -> {
                    val plan = planResult.value
                    val statusResult = getStatusUseCase(planId)
                    val status = (statusResult as? AppResult.Success)?.value
                    val pendingTasks = ((getNextTasksUseCase() as? AppResult.Success)?.value.orEmpty())
                        .filter { it.planId == planId && it.status == TaskStatus.PENDING }
                    val authTier = enrollmentStore.activeRecord()?.authTier?.let {
                        AuthTier.fromWireValue(it)
                    }

                    _planDetailState.value = UiState.Success(
                        PlanDetailData(
                            plan = plan,
                            status = status,
                            pendingTasks = pendingTasks,
                            authTier = authTier,
                        ),
                    )
                }
            }
        }
    }

    /**
     * Moves a single [TaskStatus.PENDING] task to [TaskStatus.IN_PROGRESS] (`SET_IN_PROGRESS`).
     */
    fun startTask(task: Task, typedHeadConfirmation: String? = null) {
        val current = (_planDetailState.value as? UiState.Success)?.data ?: return
        if (current.startingTaskId != null) return
        viewModelScope.launch {
            _planDetailState.value = UiState.Success(
                current.copy(startingTaskId = task.id, transitionFeedback = null),
            )
            val result = setTaskStatusUseCase(
                target = SetTaskStatusTarget(
                    taskId = task.id,
                    plan = task.planId,
                    order = task.order,
                    branch = task.branch,
                    headSha = task.headSha,
                    currentStatus = task.status,
                    targetStatus = TaskStatus.IN_PROGRESS,
                ),
                typedHeadConfirmation = typedHeadConfirmation,
            )

            val latest = (_planDetailState.value as? UiState.Success)?.data ?: current
            when (result) {
                is SetTaskStatusResult.Executed -> {
                    when (val outcome = result.outcome) {
                        is OperationOutcome.Succeeded -> {
                            val refreshedTasks = (getNextTasksUseCase() as? AppResult.Success)?.value
                                ?.filter { it.planId == latest.plan.id && it.status == TaskStatus.PENDING }
                                ?: latest.pendingTasks.filterNot { it.id == task.id }
                            _planDetailState.value = UiState.Success(
                                latest.copy(
                                    status = result.authoritativePlanStatus ?: latest.status,
                                    pendingTasks = refreshedTasks,
                                    startingTaskId = null,
                                    transitionFeedback = TaskTransitionFeedback(
                                        taskId = task.id,
                                        taskTitle = task.title,
                                        accepted = true,
                                        message = "Task moved to in_progress.",
                                        auditRef = outcome.auditRef,
                                    ),
                                ),
                            )
                        }
                        is OperationOutcome.Refused -> _planDetailState.value = UiState.Success(
                            latest.copy(
                                startingTaskId = null,
                                transitionFeedback = TaskTransitionFeedback(
                                    taskId = task.id,
                                    taskTitle = task.title,
                                    accepted = false,
                                    message = "Transition refused by gateway (${outcome.code}).",
                                    auditRef = outcome.auditRef,
                                    errorCode = outcome.code,
                                ),
                            ),
                        )
                        is OperationOutcome.Conflict -> _planDetailState.value = UiState.Success(
                            latest.copy(
                                startingTaskId = null,
                                transitionFeedback = TaskTransitionFeedback(
                                    taskId = task.id,
                                    taskTitle = task.title,
                                    accepted = false,
                                    message = "Transition conflict (${outcome.code}). Reload plan details.",
                                    auditRef = outcome.auditRef,
                                    errorCode = outcome.code,
                                ),
                            ),
                        )
                        is OperationOutcome.Timeout -> _planDetailState.value = UiState.Success(
                            latest.copy(
                                startingTaskId = null,
                                transitionFeedback = TaskTransitionFeedback(
                                    taskId = task.id,
                                    taskTitle = task.title,
                                    accepted = false,
                                    message = "No gateway response; verify via operation lookup before retrying.",
                                    errorCode = "TIMEOUT",
                                ),
                            ),
                        )
                        is OperationOutcome.UncertainCompletion -> _planDetailState.value = UiState.Success(
                            latest.copy(
                                startingTaskId = null,
                                transitionFeedback = TaskTransitionFeedback(
                                    taskId = task.id,
                                    taskTitle = task.title,
                                    accepted = false,
                                    message = "Transition completion could not be verified against the live branch head.",
                                    auditRef = outcome.auditRef,
                                    errorCode = "UNCERTAIN",
                                ),
                            ),
                        )
                        is OperationOutcome.RecoveryRequired -> _planDetailState.value = UiState.Success(
                            latest.copy(
                                startingTaskId = null,
                                transitionFeedback = TaskTransitionFeedback(
                                    taskId = task.id,
                                    taskTitle = task.title,
                                    accepted = false,
                                    message = "Operation requires recovery review and will not be auto-resent.",
                                    auditRef = outcome.auditRef,
                                    errorCode = outcome.code ?: "RECOVERY_REQUIRED",
                                ),
                            ),
                        )
                    }
                }
                is SetTaskStatusResult.NotAuthorized -> _planDetailState.value = UiState.Success(
                    latest.copy(
                        startingTaskId = null,
                        transitionFeedback = TaskTransitionFeedback(
                            taskId = task.id,
                            taskTitle = task.title,
                            accepted = false,
                            message = describeMintRefusal(result.reason),
                        ),
                    ),
                )
                is SetTaskStatusResult.InvalidTransition -> _planDetailState.value = UiState.Success(
                    latest.copy(
                        startingTaskId = null,
                        transitionFeedback = TaskTransitionFeedback(
                            taskId = task.id,
                            taskTitle = task.title,
                            accepted = false,
                            message = result.reason,
                            errorCode = "INVALID_TRANSITION",
                        ),
                    ),
                )
                is SetTaskStatusResult.InvalidTarget -> _planDetailState.value = UiState.Success(
                    latest.copy(
                        startingTaskId = null,
                        transitionFeedback = TaskTransitionFeedback(
                            taskId = task.id,
                            taskTitle = task.title,
                            accepted = false,
                            message = result.reason,
                            errorCode = "INVALID_TARGET",
                        ),
                    ),
                )
                is SetTaskStatusResult.UnsupportedByContract -> _planDetailState.value = UiState.Success(
                    latest.copy(
                        startingTaskId = null,
                        transitionFeedback = TaskTransitionFeedback(
                            taskId = task.id,
                            taskTitle = task.title,
                            accepted = false,
                            message = result.reason,
                            errorCode = "UNSUPPORTED_BY_CONTRACT",
                        ),
                    ),
                )
            }
        }
    }

    fun validatePlan(planId: String) {
        val current = (_planDetailState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _planDetailState.value = UiState.Success(current.copy(isValidating = true))
            val result = validatePlanUseCase(planId)
            val validation = (result as? AppResult.Success)?.value

            val updated = (_planDetailState.value as? UiState.Success)?.data ?: current
            _planDetailState.value = UiState.Success(
                updated.copy(
                    isValidating = false,
                    validation = validation ?: updated.validation,
                ),
            )
        }
    }

    fun checkRender(planId: String) {
        val current = (_planDetailState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _planDetailState.value = UiState.Success(current.copy(isRenderChecking = true))
            val result = renderCheckUseCase(planId)
            val renderCheck = (result as? AppResult.Success)?.value

            val updated = (_planDetailState.value as? UiState.Success)?.data ?: current
            _planDetailState.value = UiState.Success(
                updated.copy(
                    isRenderChecking = false,
                    renderCheck = renderCheck ?: updated.renderCheck,
                ),
            )
        }
    }

    private fun describeMintRefusal(reason: MintResult): String = when (reason) {
        is MintResult.Minted -> ""
        is MintResult.HeadDrifted -> "Branch head drifted to ${reason.liveSha}. Reload and try again."
        is MintResult.HeadUnavailable -> "Live branch head unavailable: ${reason.error}"
        MintResult.Declined -> "Confirmation declined. Nothing was sent."
        is MintResult.AuthFailed -> "Authentication failed: ${reason.error}"
        MintResult.LockedOut -> "Biometric authentication is locked out."
        MintResult.KeyInvalidated -> "Device key invalidated. Re-enrol before acting."
        MintResult.NotEnrolled -> "Device is not enrolled."
        MintResult.ExpiredWhileConfirming -> "Confirmation expired before minting."
        is MintResult.TierNotAllowed -> reason.reason
        is MintResult.EnvironmentMismatch ->
            "Environment mismatch: expected ${reason.expected}, gateway is ${reason.reported}."
    }
}
