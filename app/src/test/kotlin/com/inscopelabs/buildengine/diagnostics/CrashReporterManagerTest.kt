package com.inscopelabs.buildengine.diagnostics

import com.inscopelabs.buildengine.testutil.FakeContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrashReporterManagerTest {

    private lateinit var context: FakeContext

    @Before
    fun setUp() {
        context = FakeContext()
    }

    @Test
    fun `initializes with default no-op reporter when preference is false`() {
        DiagnosticPreferences.setRemoteReportingEnabled(context, false)
        CrashReporterManager.initialize(context)

        assertFalse(CrashReporterManager.isFirebaseEnabled())
        // Should not throw
        CrashReporterManager.reportCrash(Thread.currentThread(), RuntimeException("test"))
    }

    @Test
    fun `updating preference switches between reporters`() {
        CrashReporterManager.initialize(context)
        assertFalse(CrashReporterManager.isFirebaseEnabled())

        CrashReporterManager.updateReportingPreference(context, true)
        assertTrue(CrashReporterManager.isFirebaseEnabled())
        assertTrue(DiagnosticPreferences.isRemoteReportingEnabled(context))

        CrashReporterManager.updateReportingPreference(context, false)
        assertFalse(CrashReporterManager.isFirebaseEnabled())
        assertFalse(DiagnosticPreferences.isRemoteReportingEnabled(context))
    }
}
