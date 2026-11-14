package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest

/**
 * The privileged operations of the operator surface.
 *
 * Every mutating method requires a [SignedCapability] already minted via
 * `ConfirmAndMintCapabilityUseCase`, journals the non-secret intent before any wire send,
 * sends once with a client-generated 32-hex `idempotencyKey`, and reports a typed
 * [OperationOutcome]. Success is never inferred from dispatch alone (G-08, T3-302).
 *
 * Design review §12: `verify` and `bootstrap`/`init` are deliberately absent — they
 * stay on a real machine. Work-order creation is deferred under BD-1.3 Option B (PR3-027).
 */
interface OperationRepository {

    /** Publishes a minted task branch (`POST /tasks/{id}/publish`, §3.8). */
    suspend fun publish(request: OperationRequest, capability: SignedCapability): OperationOutcome

    /** Uploads an immutable submission archive (`POST /tasks/{id}/upload`, §3.10). */
    suspend fun upload(
        request: OperationRequest,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        capability: SignedCapability,
    ): OperationOutcome

    /** Reconciles merge status after operator review (`POST /tasks/{id}/reconcile`, §3.9). */
    suspend fun reconcile(
        request: OperationRequest,
        submissionId: String,
        capability: SignedCapability,
    ): OperationOutcome

    /** Issues a repository-wide halt scoped to the `engine` branch (`POST /halt`, §3.11). */
    suspend fun halt(
        request: OperationRequest,
        reason: String,
        capability: SignedCapability,
    ): OperationOutcome

    /** Clears the halt marker scoped to the `engine` branch (`POST /resume`, §3.12). */
    suspend fun resume(
        request: OperationRequest,
        reason: String,
        capability: SignedCapability,
    ): OperationOutcome

    /**
     * Returns the recorded `reason` from the most recent active terminal `HALT` in the local
     * encrypted operation journal, or `null` if no recorded halt cause exists (or the journal
     * was cleared/resumed since).
     */
    suspend fun recordedHaltCause(): String?

    /** Transitions task/plan status via `POST /plans/{id}/status` (§3.17). */
    suspend fun setTaskStatus(
        request: OperationRequest,
        targetStatus: String,
        capability: SignedCapability,
    ): OperationOutcome

    /**
     * Submits a framework decision via `POST /plans/{id}/decide` (§3.18).
     * Note: end-to-end UI dispatch remains gated on PR3-003 contract clarification (PR3-025).
     */
    suspend fun decide(
        request: OperationRequest,
        decision: String,
        notes: String? = null,
        capability: SignedCapability,
    ): OperationOutcome
}
