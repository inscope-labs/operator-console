package com.inscopelabs.buildengine.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The clock is injectable precisely so that capability TTL behaviour can be
 * exercised without waiting five minutes for it.
 */
class ClockTest {

    @Test
    fun `the system clock reports wall-clock epoch seconds`() {
        val before = Instant.now().epochSecond
        val now = SystemClock().nowEpochSeconds()
        val after = Instant.now().epochSecond

        assertTrue("clock read $now, outside [$before, $after]", now in before..after)
    }

    @Test
    fun `the system clock reports a plausible epoch`() {
        val now = SystemClock().nowEpochSeconds()

        // 2023-11-14T22:13:20Z, so a clock reading before this is broken.
        assertTrue("clock read $now", now > 1_700_000_000L)
        // 2100-01-01T00:00:00Z, so a clock reading past this is broken.
        assertTrue("clock read $now", now < 4_102_444_800L)
    }

    @Test
    fun `the fixed clock does not move until it is advanced`() {
        val clock = FixedClock(1_761_840_000L)

        assertEquals(1_761_840_000L, clock.nowEpochSeconds())
        assertEquals(1_761_840_000L, clock.nowEpochSeconds())
    }

    @Test
    fun `the fixed clock advances by whole seconds`() {
        val clock = FixedClock(1_761_840_000L)

        clock.advance(300L)

        assertEquals(1_761_840_300L, clock.nowEpochSeconds())
    }

    @Test
    fun `a capability minted now expires one TTL later`() {
        val clock = FixedClock(1_761_840_000L)
        val ttlSeconds = 300L

        val expiry = clock.nowEpochSeconds() + ttlSeconds

        assertEquals(1_761_840_300L, expiry)
        assertTrue("the capability must not be expired when it is minted", expiry > clock.nowEpochSeconds())
    }
}

/**
 * A deterministic [Clock] for tests.
 *
 * It lives in the test source set so that production code cannot depend on it, and
 * in this file so that the stage's file manifest stays as published.
 */
class FixedClock(private var now: Long) : Clock {

    override fun nowEpochSeconds(): Long = now

    /** Moves the clock forward by [seconds]. */
    fun advance(seconds: Long) {
        now += seconds
    }
}
