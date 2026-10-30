package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase

/**
 * Creates a work order. Scoped to the selected tasks only.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class CreateWorkOrderUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
) {

    suspend operator fun invoke(plan: String, taskIds: List<String>): OperationResult =
        TODO("SCAFFOLD: mint an order-scoped capability, then create the order.")
}
