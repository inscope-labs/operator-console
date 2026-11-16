package com.inscopelabs.buildengine.manualpipeline.ui.act.states

import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind

data class ActConfirmingState(
    val actionKind: ActActionKind,
    val task: ManualTask,
    val transitionKind: TransitionKind? = null,
    val manifest: SubmissionManifest? = null,
    val confirmationTier: ConfirmationTier?,
    val hardwareAvailable: Boolean,
    val tierAllowed: Boolean,
    val tierReason: String,
    val expectedEnvironment: Environment,
    val reportedEnvironment: Environment,
    val typedHeadInput: String = "",
    val requiredHeadPrefix: String = "",
    val isSubmitting: Boolean = false,
    val validationError: String? = null,
)
