package com.inscopelabs.buildengine.manualpipeline.data.journal

import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.data.local.OperationIntent
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest

/**
 * Builds non-secret [OperationIntent] fingerprints matching the host encrypted journal (T3-202).
 */
object JournalIntentBuilder {

    fun forTransition(request: TransitionRequest): OperationIntent {
        val op = when (request.kind) {
            TransitionKind.SET_IN_PROGRESS, TransitionKind.SET -> OperationType.SET_IN_PROGRESS
            TransitionKind.SET_DONE -> OperationType.SET_DONE
        }
        return OperationIntent(
            operation = op,
            plan = request.planId,
            task = request.taskId,
            branch = request.branch,
            expectedHead = request.expectedHead,
            targetStatus = request.kind.wireTargetStatus,
        )
    }

    fun forUpload(
        planId: String,
        taskId: String,
        branch: String,
        expectedHead: String,
        submissionId: String,
        zipSha256: String,
    ): OperationIntent = OperationIntent(
        operation = OperationType.UPLOAD,
        plan = planId,
        task = taskId,
        branch = branch,
        expectedHead = expectedHead,
        submissionId = submissionId,
        zipSha256 = zipSha256.lowercase(),
    )
}
