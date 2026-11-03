package com.inscopelabs.buildengine.boot

import com.inscopelabs.buildengine.testutil.FakeActivity
import com.inscopelabs.buildengine.testutil.FakeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BootRouteTest {

    private lateinit var fakeContext: FakeContext
    private lateinit var fakeActivity: FakeActivity

    @Before
    fun setUp() {
        fakeContext = FakeContext()
        fakeActivity = FakeActivity(fakeContext)
        BootGuard.clear(fakeContext)
    }

    @Test
    fun `redirectIfNeeded returns false when there is no failure`() {
        val redirected = BootRoute.redirectIfNeeded(fakeActivity)
        assertFalse(redirected)
        assertEquals(0, fakeActivity.startedIntents.size)
        assertFalse(fakeActivity.isFinished)
    }

    @Test
    fun `redirectIfNeeded starts RecoveryActivity and finishes activity when failure is present`() {
        BootGuard.recordFailure(fakeContext, "migration_failure", IllegalStateException("Corrupt db"))

        val redirected = BootRoute.redirectIfNeeded(fakeActivity)
        assertTrue(redirected)

        assertEquals(1, fakeActivity.startedIntents.size)
        assertTrue(fakeActivity.isFinished)

        val intent = fakeActivity.startedIntents.first()
        assertNotNull(intent)
    }
}
