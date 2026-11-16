package com.inscopelabs.buildengine.manualpipeline.ui.act.states

import com.inscopelabs.buildengine.manualpipeline.domain.error.ErrorPresentation
import com.inscopelabs.buildengine.manualpipeline.domain.model.GuardRefusal
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.VerifyTransitionUseCase

data class ActOutcomeState(
    val actionKind: ActActionKind,
    val taskId: String,
    val outcome: OperationOutcome,
    val guardRefusal: GuardRefusal? = null,
    val errorPresentation: ErrorPresentation? = null,
    val verificationReport: VerifyTransitionUseCase.VerificationReport? = null,
    val blockedReason: String? = null,
    val blockedCode: String? = null,
)
