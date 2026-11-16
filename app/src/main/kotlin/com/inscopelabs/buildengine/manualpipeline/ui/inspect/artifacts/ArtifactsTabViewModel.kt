package com.inscopelabs.buildengine.manualpipeline.ui.inspect.artifacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.port.DeliverableManifestPort
import com.inscopelabs.buildengine.manualpipeline.domain.model.WorkOrder
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.InterpretWorkOrderUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ManageDeliverablesUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.validation.DeliverableValidator
import com.inscopelabs.buildengine.manualpipeline.ui.state.CanvasMode
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import com.inscopelabs.buildengine.manualpipeline.util.ZipBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ArtifactsTabViewModel @Inject constructor(
    private val interpretWorkOrderUseCase: InterpretWorkOrderUseCase,
    private val manageDeliverablesUseCase: ManageDeliverablesUseCase,
    private val deliverableManifestPort: DeliverableManifestPort,
    private val session: ManualPipelineSession,
) : ViewModel() {

    data class ArtifactsUiModel(
        val workOrder: WorkOrder? = null,
        val managed: ManageDeliverablesUseCase.ManagedDeliverables? = null,
        val archiveError: String? = null,
        val pendingBundleEntries: Map<String, Int> = emptyMap(),
        val sealedUploadedSha256: String? = null,
    )

    private val stagedRawFiles = linkedMapOf<String, ByteArray>()

    private val _uiModel = MutableStateFlow(ArtifactsUiModel())
    val uiModel: StateFlow<ArtifactsUiModel> = _uiModel.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.collect { snap ->
                val task = snap.selectedTask
                val order = snap.workOrder ?: task?.let { interpretWorkOrderUseCase(it) }
                val managed = if (order != null) {
                    manageDeliverablesUseCase(
                        workOrder = order,
                        submissionId = snap.submissionId.ifBlank { "sub-${task?.id ?: "manual"}" },
                        suppliedPaths = snap.preparedArchive?.entryPaths.orEmpty(),
                        digest = snap.preparedArchive?.digest,
                        sourceDisplayName = snap.preparedArchive?.sourceDisplayName.orEmpty(),
                    )
                } else {
                    null
                }
                _uiModel.update {
                    it.copy(
                        workOrder = order,
                        managed = managed,
                        sealedUploadedSha256 = snap.sealedUploadSha256,
                    )
                }
            }
        }
    }

    fun onArchivePicked(result: ZipBuilder.BuildResult) {
        when (result) {
            is ZipBuilder.BuildResult.Invalid -> {
                _uiModel.update { it.copy(archiveError = result.reason) }
            }
            is ZipBuilder.BuildResult.Ready -> {
                attachBuiltArchive(result.archive)
            }
        }
    }

    fun onDeliverableFilePicked(displayName: String, bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) {
            _uiModel.update {
                it.copy(archiveError = "Picked file '$displayName' was empty or exceeded archive limits.")
            }
            return
        }
        val cleanName = displayName.replace('\\', '/').trimStart('/').ifBlank { "deliverable.bin" }
        stagedRawFiles[cleanName] = bytes
        _uiModel.update {
            it.copy(
                archiveError = null,
                pendingBundleEntries = stagedRawFiles.mapValues { e -> e.value.size },
            )
        }
    }

    fun clearStagedFiles() {
        stagedRawFiles.clear()
        _uiModel.update { it.copy(pendingBundleEntries = emptyMap(), archiveError = null) }
    }

    fun buildZipFromStagedFiles() {
        val taskId = session.state.value.selectedTask?.id ?: "manual"
        when (val built = ZipBuilder.buildFromEntries(stagedRawFiles, "bundle-$taskId.zip")) {
            is ZipBuilder.BuildResult.Invalid -> {
                _uiModel.update { it.copy(archiveError = built.reason) }
            }
            is ZipBuilder.BuildResult.Ready -> {
                attachBuiltArchive(built.archive)
            }
        }
    }

    fun proceedToActCanvas() {
        session.setCanvasMode(CanvasMode.ACT)
    }

    private fun attachBuiltArchive(archive: ZipBuilder.BuiltArchive) {
        val snap = session.state.value
        val task = snap.selectedTask
        val order = snap.workOrder ?: task?.let { interpretWorkOrderUseCase(it) }
        val subId = snap.submissionId.ifBlank { "sub-${task?.id ?: "manual"}" }
        val managed = order?.let {
            manageDeliverablesUseCase(
                workOrder = it,
                submissionId = subId,
                suppliedPaths = archive.entryPaths,
                digest = archive.digest,
                sourceDisplayName = archive.sourceDisplayName,
            )
        }
        session.attachArchive(
            archive = archive,
            submissionId = subId,
            manifest = managed?.manifest,
        )
        _uiModel.update {
            it.copy(
                archiveError = null,
                managed = managed,
            )
        }
        val manifest = managed?.manifest
        if (manifest != null) {
            viewModelScope.launch {
                deliverableManifestPort.saveManifest(manifest, managed.deliverables)
            }
        }
    }
}
