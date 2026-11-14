package com.inscopelabs.buildengine.ui.decision

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.domain.usecase.write.DecideFrameworkUseCase
import com.inscopelabs.buildengine.domain.usecase.write.DecideResult
import com.inscopelabs.buildengine.domain.usecase.write.DecideTarget
import com.inscopelabs.buildengine.domain.usecase.write.FrameworkDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DecisionFormState(
    val decisionId: String = "",
    val plan: String = "",
    val task: String = "",
    val branch: String = "",
    val headSha: String = "",
    val order: String = "",
    val selectedDecision: FrameworkDecision = FrameworkDecision.APPROVED,
    val attribution: String = "",
    val rationale: String = "",
    val scope: String = "",
    val evidence: String = "",
    val notes: String = "",
    val isSubmitting: Boolean = false,
    val contractBlockedReason: String? = DecideFrameworkUseCase.PR3_003_BLOCKER_MESSAGE,
    val lastResult: DecideResult? = null,
) {
    val hasRequiredFields: Boolean
        get() = plan.isNotBlank() &&
            task.isNotBlank() &&
            branch.isNotBlank() &&
            headSha.isNotBlank() &&
            order.isNotBlank() &&
            attribution.isNotBlank() &&
            rationale.isNotBlank() &&
            scope.isNotBlank() &&
            evidence.isNotBlank()

    val canSubmit: Boolean
        get() = !isSubmitting && hasRequiredFields && contractBlockedReason == null
}

/**
 * State holder for owner framework decisions (`POST /plans/{id}/decide`, T3-404 / PR3-025).
 *
 * Captures the required attribution, rationale, scope, and evidence fields while failing closed
 * until the PR3-003 schema/route contract blocker is resolved.
 */
@HiltViewModel
class DecisionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val decideFrameworkUseCase: DecideFrameworkUseCase,
) : ViewModel() {

    private val initialDecisionId: String = savedStateHandle.get<String>("decisionId").orEmpty()

    private val _uiState = MutableStateFlow(
        DecisionFormState(
            decisionId = initialDecisionId,
            contractBlockedReason = if (decideFrameworkUseCase.isContractSettled) {
                null
            } else {
                DecideFrameworkUseCase.PR3_003_BLOCKER_MESSAGE
            },
        ),
    )
    val uiState: StateFlow<DecisionFormState> = _uiState.asStateFlow()

    fun updatePlan(value: String) = _uiState.update { it.copy(plan = value, lastResult = null) }
    fun updateTask(value: String) = _uiState.update { it.copy(task = value, lastResult = null) }
    fun updateBranch(value: String) = _uiState.update { it.copy(branch = value, lastResult = null) }
    fun updateHeadSha(value: String) = _uiState.update { it.copy(headSha = value, lastResult = null) }
    fun updateOrder(value: String) = _uiState.update { it.copy(order = value, lastResult = null) }
    fun selectDecision(value: FrameworkDecision) = _uiState.update { it.copy(selectedDecision = value, lastResult = null) }
    fun updateAttribution(value: String) = _uiState.update { it.copy(attribution = value, lastResult = null) }
    fun updateRationale(value: String) = _uiState.update { it.copy(rationale = value, lastResult = null) }
    fun updateScope(value: String) = _uiState.update { it.copy(scope = value, lastResult = null) }
    fun updateEvidence(value: String) = _uiState.update { it.copy(evidence = value, lastResult = null) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value, lastResult = null) }

    fun submitDecision(typedHeadConfirmation: String? = null) {
        val current = _uiState.value
        if (current.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, lastResult = null) }
            val result = decideFrameworkUseCase(
                target = DecideTarget(
                    plan = current.plan,
                    task = current.task,
                    branch = current.branch,
                    headSha = current.headSha,
                    order = current.order,
                    decision = current.selectedDecision,
                    attribution = current.attribution,
                    rationale = current.rationale,
                    scope = current.scope,
                    evidence = current.evidence,
                    notes = current.notes.takeIf { it.isNotBlank() },
                ),
                typedHeadConfirmation = typedHeadConfirmation,
            )
            _uiState.update { it.copy(isSubmitting = false, lastResult = result) }
        }
    }
}
