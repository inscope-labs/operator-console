package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase

/**
 * Publishes a minted branch to the public repository.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: this is the only action that crosses to the public repository.
 * It runs the full guard set G1-G13 on the gateway side and needs a strong confirm.
 */
class PublishTaskUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
) {

    suspend operator fun invoke(taskId: String, plan: String, order: String): OperationResult =
        TODO("SCAFFOLD: mint a scoped capability, then publish.")
}
