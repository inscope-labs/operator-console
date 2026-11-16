package com.inscopelabs.buildengine.manualpipeline.ui.act.states

import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthoritySnapshot

enum class ActActionKind {
    TRANSITION,
    UPLOAD,
}

data class ActIdleState(
    val selectedTask: ManualTask? = null,
    val manifest: SubmissionManifest? = null,
    val authority: AuthoritySnapshot? = null,
    val canTransition: Boolean = false,
    val canUpload: Boolean = false,
    val permittedTransitions: List<TransitionKind> = emptyList(),
    val readOnlyReason: String? = null,
    val leaseOwnerLabel: String = "Single-operator lease ready",
)
