package com.inscopelabs.abx.clipinbox.diagnostics

import android.content.Context

object CrashReporterManager {
    private lateinit var activeReporter: CrashReporter
    private var isFirebaseEnabled = false

    fun initialize(context: Context) {
        isFirebaseEnabled = DiagnosticPreferences.isRemoteReportingEnabled(context)
        activeReporter = if (isFirebaseEnabled) {
            FirebaseCrashReporter(context).apply { setEnabled(true) }
        } else {
            NoOpCrashReporter()
        }
        activeReporter.initialize()
    }

    fun reportCrash(thread: Thread, throwable: Throwable) {
        activeReporter.reportCrash(thread, throwable)
    }

    fun updateReportingPreference(context: Context, enabled: Boolean) {
        isFirebaseEnabled = enabled
        DiagnosticPreferences.setRemoteReportingEnabled(context, enabled)
        
        activeReporter = if (enabled) {
            FirebaseCrashReporter(context).apply { setEnabled(true) }
        } else {
            NoOpCrashReporter()
        }
        activeReporter.initialize()
    }
    
    fun isFirebaseEnabled(): Boolean = isFirebaseEnabled
}
