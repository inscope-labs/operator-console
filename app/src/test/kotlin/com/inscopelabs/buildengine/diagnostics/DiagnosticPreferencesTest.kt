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
    fun `gateway activity log is disabled by default`() {
        assertFalse(DiagnosticPreferences.isGatewayActivityLogEnabled(context))
    }

    @Test
    fun `setGatewayActivityLogEnabled updates preference value`() {
        DiagnosticPreferences.setGatewayActivityLogEnabled(context, true)
        assertTrue(DiagnosticPreferences.isGatewayActivityLogEnabled(context))

        DiagnosticPreferences.setGatewayActivityLogEnabled(context, false)
        assertFalse(DiagnosticPreferences.isGatewayActivityLogEnabled(context))
    }
}
