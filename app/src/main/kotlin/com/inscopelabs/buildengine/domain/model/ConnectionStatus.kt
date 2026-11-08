package com.inscopelabs.buildengine.domain.model

/**
 * Whether the console is talking to a gateway.
 *
 * Fed by config ([GatewayBaseUrlStore]) and the last successful health probe
 * ([HealthSnapshotStore]). Shown in the shell banner (Stage 5).
 */
sealed interface ConnectionStatus {

    /** No usable gateway base URL is configured. */
    data object NotConfigured : ConnectionStatus

    /**
     * A base URL is configured but there is no successful health snapshot
     * (never tested, or the last test failed / was cleared).
     */
    data object Unreachable : ConnectionStatus

    /**
     * Last successful health probe is retained.
     *
     * @param environment gateway `environment` from contract §3.1 (`test` or `production`)
     * @param version gateway version string
     */
    data class Connected(
        val environment: String,
        val version: String = "",
    ) : ConnectionStatus

    /**
     * Gateway is reachable, but reported environment does not match configured expected environment.
     * Privileged actions are disabled per T2-201.
     */
    data class EnvironmentMismatch(
        val expected: String,
        val reported: String,
    ) : ConnectionStatus
}
