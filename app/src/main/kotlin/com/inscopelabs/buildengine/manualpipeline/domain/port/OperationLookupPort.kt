package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus

/**
 * Read-only port for Gateway A operation lookup (`GET /operations/{id}`).
 */
interface OperationLookupPort {
    suspend fun lookupOperation(idempotencyKey: String): Result<RecoveryStatus>
}
