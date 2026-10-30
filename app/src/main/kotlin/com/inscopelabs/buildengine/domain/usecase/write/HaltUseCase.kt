package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase

/**
 * Repository-wide stop.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: a halt affects every plan and therefore needs an explicitly,
 * separately-scoped capability — not a task-scoped one.
 */
class HaltUseCase(
    private val operationRepository: OperationRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
) {

    suspend operator fun invoke(plan: String, scope: String): OperationResult =
        TODO("SCAFFOLD: mint a halt-scoped capability, then halt.")
}
