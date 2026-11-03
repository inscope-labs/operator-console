package com.inscopelabs.buildengine.core.security

/**
 * Confirmation tiers for operator capability signing.
 *
 * docs/gateway-contract.md §3.15, ADR 0007.
 * - [HARDWARE_CONFIRM]: Keystore-enforced per-use user authentication.
 * - [IN_APP_CONFIRM]: In-app operator confirmation (degraded confirmation, reads remain active).
 */
enum class AuthTier(val wireValue: String) {
    HARDWARE_CONFIRM("hardware_confirm"),
    IN_APP_CONFIRM("in_app_confirm");

    companion object {
        fun fromWireValue(value: String): AuthTier? =
            entries.firstOrNull { it.wireValue.equals(value.trim(), ignoreCase = true) }
    }
}
