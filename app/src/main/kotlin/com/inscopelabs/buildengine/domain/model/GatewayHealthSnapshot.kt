package com.inscopelabs.buildengine.domain.model

/**
 * Last successful gateway health probe (contract §3.1).
 *
 * Persisted so the shell status banner (Stage 5) can show Connected without
 * re-dialling on every composition. Cleared when the operator clears the base URL
 * or when a subsequent test fails.
 */
data class GatewayHealthSnapshot(
    val status: String,
    val environment: String,
    val version: String,
    /** Gateway-reported epoch millis (contract timestamp). */
    val timestampMillis: Long,
    /** Device clock when the probe succeeded. */
    val probedAtEpochSeconds: Long,
) {
    val isTestEnvironment: Boolean
        get() = environment.equals("test", ignoreCase = true)

    val isProductionEnvironment: Boolean
        get() = environment.equals("production", ignoreCase = true)
}
