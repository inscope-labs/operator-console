package com.inscopelabs.buildengine.core.security

import com.inscopelabs.buildengine.capability.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-operation confirmation tier policy (Stage 3.5, T3-501; BD-3.1 option (a)).
 */
class ConfirmationTierPolicyTest {

    private fun policy(hardware: Boolean) = ConfirmationTierPolicy(hardwareConfirmAvailable = { hardware })

    @Test
    fun `publish accepts both tiers regardless of hardware`() {
        for (hardware in listOf(true, false)) {
            for (tier in listOf(AuthTier.HARDWARE_CONFIRM, AuthTier.IN_APP_CONFIRM)) {
                val decision = policy(hardware).resolve(OperationType.PUBLISH, tier)
                assertTrue("PUBLISH with $tier must be allowed", decision.allowed)
                assertFalse(decision.explicitDowngrade)
            }
        }
    }

    @Test
    fun `decide refuses an in_app tier when the device can provide hardware confirmation`() {
        val decision = policy(hardware = true).resolve(OperationType.DECIDE, AuthTier.IN_APP_CONFIRM)
        assertFalse(decision.allowed)
        assertTrue(decision.hardwareAvailable)
        assertTrue(decision.reason.contains("hardware_confirm"))
    }

    @Test
    fun `decide allows a hardware tier when hardware is available`() {
        val decision = policy(hardware = true).resolve(OperationType.DECIDE, AuthTier.HARDWARE_CONFIRM)
        assertTrue(decision.allowed)
        assertFalse(decision.explicitDowngrade)
    }

    @Test
    fun `decide on a device without biometric hardware allows in_app with an explicit downgrade`() {
        val decision = policy(hardware = false).resolve(OperationType.DECIDE, AuthTier.IN_APP_CONFIRM)
        assertTrue("A device without hardware must not be locked out", decision.allowed)
        assertTrue("The downgrade must be explicit, never silent", decision.explicitDowngrade)
        assertTrue(decision.reason.contains("explicit downgrade"))
    }

    @Test
    fun `resume is governed exactly like decide`() {
        val strict = policy(hardware = true).resolve(OperationType.RESUME, AuthTier.IN_APP_CONFIRM)
        assertFalse(strict.allowed)

        val allowed = policy(hardware = true).resolve(OperationType.RESUME, AuthTier.HARDWARE_CONFIRM)
        assertTrue(allowed.allowed)

        val degraded = policy(hardware = false).resolve(OperationType.RESUME, AuthTier.IN_APP_CONFIRM)
        assertTrue(degraded.allowed)
        assertTrue(degraded.explicitDowngrade)
    }

    @Test
    fun `a null tier is refused when hardware is available`() {
        val decision = policy(hardware = true).resolve(OperationType.DECIDE, null)
        assertFalse(decision.allowed)
    }

    @Test
    fun `a provider that throws is treated as hardware unavailable, not a crash`() {
        val throwing = ConfirmationTierPolicy(hardwareConfirmAvailable = { error("biometric service down") })
        val decision = throwing.resolve(OperationType.DECIDE, AuthTier.IN_APP_CONFIRM)
        assertTrue(decision.allowed)
        assertFalse(decision.hardwareAvailable)
        assertTrue(decision.explicitDowngrade)
    }

    @Test
    fun `explainForSummary covers every case`() {
        assertTrue(
            ConfirmationTierPolicy.explainForSummary(OperationType.PUBLISH, AuthTier.HARDWARE_CONFIRM)
                .contains("accepts both confirmation tiers"),
        )
        assertTrue(
            ConfirmationTierPolicy.explainForSummary(OperationType.DECIDE, AuthTier.HARDWARE_CONFIRM)
                .contains("requires hardware_confirm"),
        )
        assertTrue(
            ConfirmationTierPolicy.explainForSummary(OperationType.RESUME, AuthTier.IN_APP_CONFIRM)
                .contains("in_app_confirm"),
        )
        assertTrue(
            ConfirmationTierPolicy.explainForSummary(OperationType.PUBLISH, null)
                .contains("not enrolled"),
        )
    }
}
