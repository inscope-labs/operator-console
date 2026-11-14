package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.OperationResult

/**
 * The privileged operations of the operator surface.
 *
 * `publish` is implemented (Stage 3.3, journal-backed, returns [OperationOutcome]).
 *
 * SCAFFOLD PLACEHOLDER — placeholder only for the remaining operations (Stages 3.4 and 3.5).
 *
 * Design review §12: `verify` and `bootstrap`/`init` are deliberately absent — they
 * stay on a real machine.
 */
interface OperationRepository {

    /**
     * Publishes the minted task branch. The caller has already minted [capability] through the
     * confirmation path; this method journals the intent, sends once and reports a typed outcome.
     * Success is never reported from dispatch alone (G-08, T3-302).
     */
    suspend fun publish(request: OperationRequest, capability: SignedCapability): OperationOutcome

    suspend fun upload(request: OperationRequest): OperationResult

    suspend fun reconcile(request: OperationRequest): OperationResult

    suspend fun halt(request: OperationRequest): OperationResult

    suspend fun resume(request: OperationRequest): OperationResult

    suspend fun decide(request: OperationRequest): OperationResult
}
