package com.inscopelabs.buildengine.diagnostics

import com.inscopelabs.buildengine.testutil.FakeContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticPreferencesTest {

    private lateinit var context: FakeContext

    @Before
    fun setUp() {
        context = FakeContext()
    }

    @Test
    fun `remote reporting is disabled by default`() {
        assertFalse(DiagnosticPreferences.isRemoteReportingEnabled(context))
    }

    @Test
    fun `setRemoteReportingEnabled updates preference value`() {
        DiagnosticPreferences.setRemoteReportingEnabled(context, true)
        assertTrue(DiagnosticPreferences.isRemoteReportingEnabled(context))

        DiagnosticPreferences.setRemoteReportingEnabled(context, false)
        assertFalse(DiagnosticPreferences.isRemoteReportingEnabled(context))
    }
}
