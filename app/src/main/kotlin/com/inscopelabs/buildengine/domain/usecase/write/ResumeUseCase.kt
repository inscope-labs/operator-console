package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase

/**
 * Clears the halt marker.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class ResumeUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
) {

    suspend operator fun invoke(plan: String, scope: String): OperationResult =
        TODO("SCAFFOLD: mint a resume-scoped capability, then resume.")
}
