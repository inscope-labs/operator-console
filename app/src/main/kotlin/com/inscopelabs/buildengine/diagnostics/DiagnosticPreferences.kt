package com.inscopelabs.buildengine.diagnostics

import android.content.Context

/**
 * Manages diagnostic preferences (local redacted diagnostics only per BD-2.3).
 */
object DiagnosticPreferences {
    private const val PREFS_NAME = "abx_diagnostics_prefs"

    fun isGatewayActivityLogEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(DiagnosticSettings.PREF_GATEWAY_ACTIVITY_LOG, false)
    }

    fun setGatewayActivityLogEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(DiagnosticSettings.PREF_GATEWAY_ACTIVITY_LOG, enabled).apply()
    }
}
