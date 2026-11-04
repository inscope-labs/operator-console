package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.HealthSnapshotStore
import com.inscopelabs.buildengine.domain.model.ConnectionStatus
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import com.inscopelabs.buildengine.domain.repository.ConnectionStatusRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Derives [ConnectionStatus] from config + last successful health probe.
 *
 * - Not configured → [ConnectionStatus.NotConfigured]
 * - Configured, no successful health snapshot → [ConnectionStatus.Unreachable]
 * - Successful health retained → [ConnectionStatus.Connected]
 */
@Singleton
class ConnectionStatusRepositoryImpl @Inject constructor(
    baseUrlStore: GatewayBaseUrlStore,
    healthSnapshotStore: HealthSnapshotStore,
) : ConnectionStatusRepository {

    override val status: Flow<ConnectionStatus> = combine(
        baseUrlStore.baseUrlFlow,
        healthSnapshotStore.snapshotFlow,
    ) { baseUrl, snapshot ->
        deriveConnectionStatus(baseUrl, snapshot)
    }
}

/**
 * Pure mapping used by [ConnectionStatusRepositoryImpl] and unit tests.
 */
fun deriveConnectionStatus(
    baseUrl: String,
    snapshot: GatewayHealthSnapshot?,
): ConnectionStatus = when {
    !GatewayEndpoints.isConfigured(baseUrl) -> ConnectionStatus.NotConfigured
    snapshot == null -> ConnectionStatus.Unreachable
    else -> ConnectionStatus.Connected(
        environment = snapshot.environment.ifEmpty { "unknown" },
        version = snapshot.version,
    )
}
