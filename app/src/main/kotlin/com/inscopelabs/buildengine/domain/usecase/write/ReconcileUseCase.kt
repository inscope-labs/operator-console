package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase

/**
 * Reads merge status and finalises task state after the operator's review.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class ReconcileUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
) {

    suspend operator fun invoke(taskId: String, plan: String): OperationResult =
        TODO("SCAFFOLD: mint a reconcile-scoped capability, then reconcile.")
}
