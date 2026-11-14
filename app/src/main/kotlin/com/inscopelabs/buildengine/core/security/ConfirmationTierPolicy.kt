package com.inscopelabs.buildengine.core.security

import com.inscopelabs.buildengine.capability.model.OperationType

/**
 * The outcome of applying the per-operation confirmation tier policy (BD-3.1).
 */
data class TierDecision(
    val op: OperationType,
    /** Whether the device can provide a hardware_confirm gesture at all. */
    val hardwareAvailable: Boolean,
    /** Whether minting may proceed with the enrolled tier. */
    val allowed: Boolean,
    /**
     * True when an operation that prefers hardware_confirm proceeds with
     * in_app_confirm because the device cannot provide hardware confirmation.
     * This must never be a silent downgrade: [reason] is shown on the
     * confirmation summary (T3-501).
     */
    val explicitDowngrade: Boolean,
    /** Why this tier decision was reached; shown on the confirmation summary. */
    val reason: String,
)

/**
 * Per-operation minimum confirmation tier policy (Stage 3.5, T3-501; decision BD-3.1 option (a)).
 *
 * BD-3.1(a): staging and production accept both confirmation tiers for every
 * operation, with one exception — `DECIDE` and `RESUME` require `hardware_confirm`
 * *when the device can provide it*. A device without biometric hardware is never
 * locked out of acting (operator requirement), but the downgrade from
 * `hardware_confirm` to `in_app_confirm` must be explicit and visible, never silent
 * (T3-502).
 */
open class ConfirmationTierPolicy(
    private val hardwareConfirmAvailable: () -> Boolean = { false },
) {

    /**
     * Resolves whether [enrolledTier] may mint a capability for [op] on this device.
     * A hardware-provider failure is treated as "hardware not available": the
     * degraded tier stays available, and a hardware_confirm key still works through
     * the biometric gate's own refusal path. When hardware IS available, an
     * in_app_confirm key is refused for DECIDE/RESUME (fail closed, never a silent
     * downgrade).
     */
    open fun resolve(op: OperationType, enrolledTier: AuthTier?): TierDecision {
        val hardware = runCatching(hardwareConfirmAvailable).getOrDefault(false)
        return when (op) {
            OperationType.DECIDE, OperationType.RESUME -> {
                if (hardware) {
                    val allowed = enrolledTier == AuthTier.HARDWARE_CONFIRM
                    TierDecision(
                        op = op,
                        hardwareAvailable = true,
                        allowed = allowed,
                        explicitDowngrade = false,
                        reason = if (allowed) {
                            "BD-3.1(a): ${op.name} requires hardware_confirm; this device provides it and the enrolled key uses it."
                        } else {
                            "BD-3.1(a): ${op.name} requires hardware_confirm, this device can provide it, but the enrolled key uses in_app_confirm. Re-enrol the device with a hardware_confirm key before deciding or resuming."
                        },
                    )
                } else {
                    TierDecision(
                        op = op,
                        hardwareAvailable = false,
                        allowed = true,
                        explicitDowngrade = enrolledTier == AuthTier.IN_APP_CONFIRM,
                        reason = "BD-3.1(a): this device cannot provide hardware_confirm (no usable biometric hardware); in_app_confirm is the explicit downgrade for ${op.name}.",
                    )
                }
            }
            else -> TierDecision(
                op = op,
                hardwareAvailable = hardware,
                allowed = true,
                explicitDowngrade = false,
                reason = "BD-3.1(a): staging and production accept both confirmation tiers for ${op.name}.",
            )
        }
    }

    companion object {
        /**
         * The static "tier and why" line for a confirmation summary (T3-501).
         * Pure function of the enrolled tier; the device-capability-dependent
         * refinement is applied by [resolve] at mint time.
         */
        fun explainForSummary(op: OperationType, tier: AuthTier?): String = when {
            tier == null ->
                "Confirmation tier unknown: the device is not enrolled, so confirmation is disabled."
            op == OperationType.DECIDE || op == OperationType.RESUME ->
                if (tier == AuthTier.HARDWARE_CONFIRM) {
                    "BD-3.1(a): ${op.name} requires hardware_confirm; the enrolled key uses it."
                } else {
                    "BD-3.1(a): ${op.name} prefers hardware_confirm; the enrolled key uses in_app_confirm, which is accepted only when the device cannot provide hardware confirmation."
                }
            else ->
                "BD-3.1(a): ${op.name} accepts both confirmation tiers; the enrolled key uses ${tier.wireValue}."
        }
    }
}
