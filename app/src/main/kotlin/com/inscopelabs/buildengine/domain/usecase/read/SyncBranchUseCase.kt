package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.core.network.GatewayFailureState
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.SyncStatus
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Verifies consumer repository synchronization (`POST /sync`, §3.13; T3-405).
 * Read-only fast-forward check requiring no write capability.
 */
class SyncBranchUseCase(private val planRepository: PlanRepository) {

    suspend operator fun invoke(branch: String = "main"): AppResult<SyncStatus> {
        val trimmed = branch.trim()
        if (trimmed.isEmpty()) {
            return AppResult.Failure(
                code = GatewayFailureState.FORMAT.code,
                message = "Branch name cannot be blank",
            )
        }
        return planRepository.sync(trimmed)
    }
}
