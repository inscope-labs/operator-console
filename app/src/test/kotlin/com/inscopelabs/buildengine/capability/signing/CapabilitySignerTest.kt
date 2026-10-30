package com.inscopelabs.buildengine.capability.signing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers both signing directions behind the CapabilitySigner seam: Model B (the
 * console signs with the device key) and Model A (the gateway mints).
 *
 * SCAFFOLD PLACEHOLDER — replace with a verify-on-gateway round trip.
 */
class CapabilitySignerTest {

    @Test
    fun `both directions implement the signer contract`() {
        val signers: List<CapabilitySigner> = listOf(
            DeviceKeyCapabilitySigner(),
            HmacCapabilitySigner(),
        )
        assertEquals(2, signers.size)
        assertEquals(
            listOf(DeviceKeyCapabilitySigner::class, HmacCapabilitySigner::class),
            signers.map { it::class },
        )
    }
}
