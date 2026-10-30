package com.inscopelabs.buildengine.capability.model

/**
 * The closed enumeration of operations a capability may authorise.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
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
    NEW_PLAN,
}
