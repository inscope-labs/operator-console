package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest
import com.inscopelabs.buildengine.manualpipeline.domain.port.MintedCapabilityHandle
import com.inscopelabs.buildengine.manualpipeline.domain.port.StatusTransitionPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates guarded status transitions through [StatusTransitionPort] -> host journaled executor.
 */
@Singleton
class TransitionRepositoryImpl @Inject constructor(
    private val statusTransitionPort: StatusTransitionPort,
) {
    suspend fun submit(
        request: TransitionRequest,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome = statusTransitionPort.submitTransition(request, mintedCapability)
}
