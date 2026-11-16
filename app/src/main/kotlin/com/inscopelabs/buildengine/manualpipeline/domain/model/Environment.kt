package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Target execution environment identity for I-11 environment isolation.
 */
enum class Environment(val wireValue: String, val displayLabel: String) {
    TEST("test", "TEST"),
    STAGING("staging", "STAGING"),
    PRODUCTION("production", "PRODUCTION"),
    UNPROBED("unprobed", "UNPROBED");

    companion object {
        fun fromWire(raw: String?): Environment {
            val norm = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it != UNPROBED && it.wireValue == norm } ?: UNPROBED
        }
    }
}
