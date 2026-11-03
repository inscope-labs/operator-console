package com.inscopelabs.buildengine.boot

import com.inscopelabs.buildengine.testutil.FakeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BootGuardTest {

    private lateinit var context: FakeContext

    @Before
    fun setUp() {
        context = FakeContext()
        BootGuard.clear(context)
    }

    @Test
    fun `stage tracking logs without throwing`() {
        BootGuard.stageStart("test_stage")
        BootGuard.stageSuccess("test_stage")
    }

    @Test
    fun `no failure initially`() {
        assertFalse(BootGuard.hasFailure(context))
        assertNull(BootGuard.currentFailure(context))
    }

    @Test
    fun `recordFailure stores failure in memory and in shared preferences`() {
        val ex = IllegalStateException("database failed to open")
        BootGuard.recordFailure(context, "db_init", ex)

        assertTrue(BootGuard.hasFailure(context))

        val failure = BootGuard.currentFailure(context)
        assertNotNull(failure)
        assertEquals("db_init", failure?.stage)
        assertEquals("database failed to open", failure?.message)
        assertTrue(failure?.stackTrace?.contains("IllegalStateException") == true)
        assertTrue(failure?.timestamp?.isNotBlank() == true)

        // Verify it was written to SharedPreferences
        assertTrue(context.fakePrefs.getBoolean("has_failure", false))
        assertEquals("db_init", context.fakePrefs.getString("failure_stage", null))
        assertEquals("database failed to open", context.fakePrefs.getString("failure_message", null))
    }

    @Test
    fun `clear removes failure from memory and shared preferences`() {
        val ex = RuntimeException("crash")
        BootGuard.recordFailure(context, "init", ex)
        assertTrue(BootGuard.hasFailure(context))

        BootGuard.clear(context)
        assertFalse(BootGuard.hasFailure(context))
        assertNull(BootGuard.currentFailure(context))
        assertFalse(context.fakePrefs.getBoolean("has_failure", false))
    }

    @Test
    fun `works safely when context is null`() {
        val ex = IllegalArgumentException("null context test")
        BootGuard.recordFailure(null, "early_stage", ex)

        assertTrue(BootGuard.hasFailure(null))
        val failure = BootGuard.currentFailure(null)
        assertNotNull(failure)
        assertEquals("early_stage", failure?.stage)

        BootGuard.clear(null)
        assertFalse(BootGuard.hasFailure(null))
    }
}
