package com.inscopelabs.buildengine.manualpipeline.ui.state

import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.model.WorkOrder
import com.inscopelabs.buildengine.manualpipeline.util.ZipBuilder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory shared workspace session bridging Inspect tabs, Act confirmation flows, and Handoff
 * verification for the currently selected task without persisting volatile `.zip` payloads to disk.
 */
@Singleton
class ManualPipelineSession @Inject constructor() {

    data class Snapshot(
        val canvas: CanvasState = CanvasState(),
        val selectedTask: ManualTask? = null,
        val workOrder: WorkOrder? = null,
        val preparedArchive: ZipBuilder.BuiltArchive? = null,
        val submissionId: String = "",
        val manifest: SubmissionManifest? = null,
        val lastUploadAuditRef: String? = null,
        val lastUploadIdempotencyKey: String? = null,
        val lastTransitionAuditRef: String? = null,
        val lastTransitionIdempotencyKey: String? = null,
        val sealedUploadSha256: String? = null,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun selectTask(task: ManualTask, workOrder: WorkOrder? = null) {
        _state.update { prev ->
            val sameTask = prev.selectedTask?.id == task.id
            prev.copy(
                canvas = prev.canvas.copy(selectedTaskId = task.id),
                selectedTask = task,
                workOrder = workOrder ?: if (sameTask) prev.workOrder else null,
                preparedArchive = if (sameTask) prev.preparedArchive else null,
                manifest = if (sameTask) prev.manifest else null,
                submissionId = if (sameTask) prev.submissionId else "sub-${task.id}",
            )
        }
    }

    fun setCanvasMode(mode: CanvasMode) {
        _state.update { it.copy(canvas = it.canvas.copy(mode = mode, recoveryOpen = false, handoffOpen = false)) }
    }

    fun setInspectTab(tab: InspectTab) {
        _state.update {
            it.copy(canvas = it.canvas.copy(mode = CanvasMode.INSPECT, activeInspectTab = tab, recoveryOpen = false, handoffOpen = false))
        }
    }

    fun openRecovery(open: Boolean = true) {
        _state.update { it.copy(canvas = it.canvas.copy(recoveryOpen = open, handoffOpen = false)) }
    }

    fun openHandoff(open: Boolean = true) {
        _state.update { it.copy(canvas = it.canvas.copy(handoffOpen = open, recoveryOpen = false)) }
    }

    fun attachArchive(
        archive: ZipBuilder.BuiltArchive,
        submissionId: String,
        manifest: SubmissionManifest?,
    ) {
        _state.update {
            it.copy(
                preparedArchive = archive,
                submissionId = submissionId,
                manifest = manifest,
            )
        }
    }

    fun recordUploadEvidence(auditRef: String, idempotencyKey: String, sha256: String) {
        _state.update {
            it.copy(
                lastUploadAuditRef = auditRef,
                lastUploadIdempotencyKey = idempotencyKey,
                sealedUploadSha256 = sha256,
            )
        }
    }

    fun recordTransitionEvidence(auditRef: String, idempotencyKey: String) {
        _state.update {
            it.copy(
                lastTransitionAuditRef = auditRef,
                lastTransitionIdempotencyKey = idempotencyKey,
            )
        }
    }
}
