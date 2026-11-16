package com.inscopelabs.buildengine.manualpipeline.data.integration

import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import com.inscopelabs.buildengine.data.local.HealthSnapshotStore
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import javax.inject.Inject

/**
 * Provides expected vs gateway-reported environment state (I-11) for UI badges and session checks.
 */
class EnvironmentProviderAdapter @Inject constructor(
    private val baseUrlStore: GatewayBaseUrlStore,
    private val healthSnapshotStore: HealthSnapshotStore,
) {
    suspend fun expectedEnvironment(): Environment =
        Environment.fromWire(baseUrlStore.readExpectedEnvironment())

    suspend fun reportedEnvironment(): Environment =
        Environment.fromWire(healthSnapshotStore.read()?.environment)

    suspend fun isBaseUrlConfigured(): Boolean =
        baseUrlStore.read().isNotBlank()
}
