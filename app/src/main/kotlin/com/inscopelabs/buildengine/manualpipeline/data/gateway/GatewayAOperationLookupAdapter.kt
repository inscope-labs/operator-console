package com.inscopelabs.buildengine.manualpipeline.data.gateway

import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.OperationMapper
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.OperationLookupPort
import javax.inject.Inject

/**
 * Read-only Gateway A operation lookup adapter (`GET /operations/{id}`).
 */
class GatewayAOperationLookupAdapter @Inject constructor(
    private val gatewayApi: GatewayApi,
) : OperationLookupPort {

    override suspend fun lookupOperation(idempotencyKey: String): Result<RecoveryStatus> = runCatching {
        OperationMapper.toRecoveryStatus(gatewayApi.lookupOperation(idempotencyKey))
    }
}
