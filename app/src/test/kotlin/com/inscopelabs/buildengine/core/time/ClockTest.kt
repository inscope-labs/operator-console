package com.inscopelabs.buildengine.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {

    @Test
    fun `system clock returns realistic epoch seconds`() {
        val clock = SystemClock()
        val now = clock.nowEpochSeconds()
        assertTrue("Epoch seconds should be after year 2024", now > 1700000000L)
    }

    @Test
    fun `fixed clock returns configured seconds and advances correctly`() {
        val fixed = FixedClock(1761840000L)
        assertEquals(1761840000L, fixed.nowEpochSeconds())

        fixed.advanceSeconds(300L)
        assertEquals(1761840300L, fixed.nowEpochSeconds())

        fixed.setEpochSeconds(1800000000L)
        assertEquals(1800000000L, fixed.nowEpochSeconds())
    }
}
