package com.inscopelabs.buildengine.manualpipeline.data.gateway

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest
import com.inscopelabs.buildengine.manualpipeline.domain.port.ExecutorPort
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintedCapabilityHandle
import com.inscopelabs.buildengine.manualpipeline.domain.port.StatusTransitionPort
import javax.inject.Inject

/**
 * Gateway A status transition adapter routing writes through [ExecutorPort]
 * -> existing `OperationRepositoryImpl.executeOperation` (never bypasses the encrypted journal).
 */
class GatewayAStatusTransitionAdapter @Inject constructor(
    private val executorPort: ExecutorPort,
) : StatusTransitionPort {

    override suspend fun submitTransition(
        request: TransitionRequest,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome = executorPort.executeTransition(request, mintedCapability)
}
