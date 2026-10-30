package com.inscopelabs.buildengine.capability.model

/**
 * The closed enumeration of operations a capability may authorise.
 *
 * Design review §5: authorisation must never be inferred from an unrecognised
 * request, so this is an enumeration and not a free-form string.
 */
enum class OperationType {
    PUBLISH,
    UPLOAD,
    RECONCILE,
    DECIDE,
    HALT,
    RESUME,
    SYNC,
    DOCTOR,
    ORDER,
    SET_IN_PROGRESS,
    SET_DONE,
    NEW_PLAN;

    val isTaskScoped: Boolean
        get() = when (this) {
            PUBLISH,
            UPLOAD,
            RECONCILE,
            DECIDE,
            ORDER,
            SET_IN_PROGRESS,
            SET_DONE -> true
            HALT,
            RESUME,
            SYNC,
            DOCTOR,
            NEW_PLAN -> false
        }
}
