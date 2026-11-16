package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest

/**
 * Port for Gateway A status transition (`POST /plans/{id}/status`).
 */
interface StatusTransitionPort {
    suspend fun submitTransition(
        request: TransitionRequest,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome
}
