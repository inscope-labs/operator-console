package com.inscopelabs.buildengine.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.usecase.read.GetPlansUseCase
import com.inscopelabs.buildengine.domain.usecase.read.GetStatusUseCase
import com.inscopelabs.buildengine.domain.usecase.read.RenderCheckUseCase
import com.inscopelabs.buildengine.domain.usecase.read.ValidatePlanUseCase
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanDetailData(
    val plan: Plan,
    val status: PlanStatus? = null,
    val validation: PlanValidation? = null,
    val renderCheck: RenderCheck? = null,
    val isValidating: Boolean = false,
    val isRenderChecking: Boolean = false,
)

/**
 * Plan list and plan detail state holder.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val getPlansUseCase: GetPlansUseCase,
    private val planRepository: PlanRepository,
    private val getStatusUseCase: GetStatusUseCase,
    private val validatePlanUseCase: ValidatePlanUseCase,
    private val renderCheckUseCase: RenderCheckUseCase,
) : ViewModel() {

    private val _planListState = MutableStateFlow<UiState<List<Plan>>>(UiState.Idle)
    val planListState: StateFlow<UiState<List<Plan>>> = _planListState.asStateFlow()

    private val _planDetailState = MutableStateFlow<UiState<PlanDetailData>>(UiState.Idle)
    val planDetailState: StateFlow<UiState<PlanDetailData>> = _planDetailState.asStateFlow()

    init {
        loadPlans()
    }

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

                    _planDetailState.value = UiState.Success(
                        PlanDetailData(
                            plan = plan,
                            status = status,
                        ),
                    )
                }
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
}
