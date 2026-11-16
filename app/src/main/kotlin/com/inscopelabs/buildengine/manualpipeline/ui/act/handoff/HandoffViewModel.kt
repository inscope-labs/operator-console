package com.inscopelabs.buildengine.manualpipeline.ui.act.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import com.inscopelabs.buildengine.manualpipeline.domain.port.HandoffEvidenceStore
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.VerifyHandoffUseCase
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HandoffViewModel @Inject constructor(
    private val verifyHandoffUseCase: VerifyHandoffUseCase,
    private val handoffEvidenceStore: HandoffEvidenceStore,
    private val session: ManualPipelineSession,
) : ViewModel() {

    data class HandoffUiModel(
        val latestEvidence: HandoffEvidence? = null,
        val missingItems: List<String> = emptyList(),
        val isVerifying: Boolean = false,
        val statusMessage: String? = null,
    )

    private val _uiModel = MutableStateFlow(HandoffUiModel())
    val uiModel: StateFlow<HandoffUiModel> = _uiModel.asStateFlow()

    init {
        viewModelScope.launch {
            val taskId = session.state.value.selectedTask?.id
            if (!taskId.isNullOrBlank()) {
                val existing = handoffEvidenceStore.latestForTask(taskId)
                _uiModel.update { it.copy(latestEvidence = existing) }
            }
        }
    }

    fun verifyHandoff() {
        val snap = session.state.value
        val task = snap.selectedTask
        if (task == null) {
            _uiModel.update {
                it.copy(
                    missingItems = listOf("selected task"),
                    statusMessage = "Select a task before assembling handoff evidence.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiModel.update { it.copy(isVerifying = true, statusMessage = null) }
            val result = verifyHandoffUseCase(
                task = task,
                submissionId = snap.submissionId,
                zipSha256 = snap.sealedUploadSha256 ?: snap.manifest?.digest?.zipSha256.orEmpty(),
                uploadAuditRef = snap.lastUploadAuditRef.orEmpty(),
                transitionAuditRef = snap.lastTransitionAuditRef.orEmpty(),
                uploadIdempotencyKey = snap.lastUploadIdempotencyKey.orEmpty(),
                transitionIdempotencyKey = snap.lastTransitionIdempotencyKey.orEmpty(),
                observedStatus = task.status,
            )
            when (result) {
                is VerifyHandoffUseCase.HandoffVerificationResult.Ready -> {
                    _uiModel.update {
                        it.copy(
                            latestEvidence = result.evidence,
                            missingItems = emptyList(),
                            isVerifying = false,
                            statusMessage = "${HandoffEvidence.READY_MARKER} verified & stored.",
                        )
                    }
                }
                is VerifyHandoffUseCase.HandoffVerificationResult.Incomplete -> {
                    _uiModel.update {
                        it.copy(
                            missingItems = result.missingItems,
                            isVerifying = false,
                            statusMessage = "Handoff incomplete until all required evidence items are verified.",
                        )
                    }
                }
            }
        }
    }

    fun close() {
        session.openHandoff(false)
    }
}
