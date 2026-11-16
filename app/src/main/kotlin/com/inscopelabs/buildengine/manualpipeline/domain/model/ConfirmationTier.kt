package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Operator capability confirmation tier (I-01 / ADR 0012).
 */
enum class ConfirmationTier(val wireValue: String) {
    HARDWARE_CONFIRM("hardware_confirm"),
    IN_APP_CONFIRM("in_app_confirm");

    companion object {
        fun fromWire(raw: String?): ConfirmationTier? =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
    }
}
