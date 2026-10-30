package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase

/**
 * Submission intake. Content-addressed and immutable.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12 / G15: a submission is never rewritten.
 */
class UploadSubmissionUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
) {

    suspend operator fun invoke(taskId: String, plan: String): OperationResult =
        TODO("SCAFFOLD: mint an upload-scoped capability, then upload.")
}
