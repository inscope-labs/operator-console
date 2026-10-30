package com.inscopelabs.buildengine.capability.model

/**
 * The closed enumeration of operations a capability may authorise.
 *
 * Design review §5: authorisation must never be inferred from an unrecognised
 * request, so this is an enumeration and not a free-form string.
 *
 * The constant name is also the wire token: `ctxop-v1` carries the operation as the
 * constant's own name, uppercase, and the codec refuses anything outside this set.
 * Renaming a constant is therefore a change to the wire format, not a refactor.
 *
 * The enumeration is also where the scope rules bite. `HALT` affects every plan in
 * the repository, so it cannot be authorised by a task-scoped capability and needs
 * one that is explicitly and separately scoped for it.
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
