package com.inscopelabs.buildengine.manualpipeline.domain.usecase

import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthorityPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuthoritySnapshot
import javax.inject.Inject

/**
 * Resolves the confirmation tier and policy explanation for an operator write action (BD-3.1(a)).
 */
class ResolveConfirmationTierUseCase @Inject constructor(
    private val authorityPort: AuthorityPort,
) {

    data class TierResolution(
        val tier: ConfirmationTier?,
        val hardwareAvailable: Boolean,
        val allowed: Boolean,
        val explicitDowngrade: Boolean,
        val reason: String,
        val authority: AuthoritySnapshot,
    )

    suspend operator fun invoke(operationName: String): TierResolution {
        val snapshot = authorityPort.currentAuthority(operationName)
        return TierResolution(
            tier = snapshot.confirmationTier,
            hardwareAvailable = snapshot.hardwareConfirmAvailable,
            allowed = snapshot.tierAllowed,
            explicitDowngrade = snapshot.explicitDowngrade,
            reason = snapshot.tierReason,
            authority = snapshot,
        )
    }
}
