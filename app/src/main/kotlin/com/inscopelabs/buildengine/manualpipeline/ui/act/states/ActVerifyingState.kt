package com.inscopelabs.buildengine.manualpipeline.ui.act.states

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.VerifyTransitionUseCase

data class ActVerifyingState(
    val actionKind: ActActionKind,
    val taskId: String,
    val planId: String,
    val branch: String,
    val expectedHeadSha: String,
    val outcome: OperationOutcome,
    val auditRef: String?,
    val isVerifying: Boolean = true,
    val verificationReport: VerifyTransitionUseCase.VerificationReport? = null,
)
