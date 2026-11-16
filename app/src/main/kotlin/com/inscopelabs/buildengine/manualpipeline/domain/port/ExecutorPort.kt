package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionRequest

/**
 * Port routing all consequential writes through the host's existing journaled executor
 * (`OperationRepositoryImpl`), enforcing single-send & idempotency invariants (I-03).
 */
interface ExecutorPort {
    suspend fun executeTransition(
        request: TransitionRequest,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome

    suspend fun executeUpload(
        planId: String,
        taskId: String,
        orderId: String,
        branch: String,
        headSha: String,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        mintedCapability: MintedCapabilityHandle,
    ): OperationOutcome

    suspend fun lookupOperation(idempotencyKey: String): OperationOutcome

    suspend fun recoverUncertainOperations(): Map<String, OperationOutcome>
}
