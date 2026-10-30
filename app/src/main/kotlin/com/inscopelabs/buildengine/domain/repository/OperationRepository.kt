package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.OperationResult

/**
 * The privileged operations of the operator surface.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: `verify` and `bootstrap`/`init` are deliberately absent — they
 * stay on a real machine.
 */
interface OperationRepository {

    suspend fun publish(request: OperationRequest): OperationResult

    suspend fun upload(request: OperationRequest): OperationResult

    suspend fun reconcile(request: OperationRequest): OperationResult

    suspend fun halt(request: OperationRequest): OperationResult

    suspend fun resume(request: OperationRequest): OperationResult

    suspend fun decide(request: OperationRequest): OperationResult
}
