package com.inscopelabs.buildengine.manualpipeline.domain.model

/**
 * Canonical Gateway A wire task statuses (gateway-contract §1.7) plus fail-closed [UNKNOWN].
 * Internal names such as `done` are never leaked across the wire boundary.
 */
enum class TaskStatus(val wireValue: String) {
    DRAFT("draft"),
    READY("ready"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    BLOCKED("blocked"),
    UNKNOWN("unknown");

    val isTerminal: Boolean
        get() = this == COMPLETED

    companion object {
        fun fromWire(raw: String?): TaskStatus {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it != UNKNOWN && it.wireValue == normalized } ?: UNKNOWN
        }
    }
}
