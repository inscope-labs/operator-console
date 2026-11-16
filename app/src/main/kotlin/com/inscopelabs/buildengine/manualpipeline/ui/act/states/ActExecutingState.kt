package com.inscopelabs.buildengine.manualpipeline.ui.act.states

data class ActExecutingState(
    val actionKind: ActActionKind,
    val taskId: String,
    val planId: String,
    val branch: String,
    val headSha: String,
    val stageLabel: String = "Minting capability & dispatching to Gateway A...",
    val bytesTotal: Long? = null,
    val isUpload: Boolean = actionKind == ActActionKind.UPLOAD,
)
