package com.inscopelabs.abx.clipinbox.diagnostics

import android.content.Context
import android.util.Log

class FirebaseCrashReporter(private val context: Context) : CrashReporter {
    private var isEnabled = false

    override fun initialize() {
        updateCrashlyticsState()
    }

    override fun reportCrash(thread: Thread, throwable: Throwable) {
        if (!isEnabled) return
        try {
            val crashlyticsClass = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstanceMethod = crashlyticsClass.getMethod("getInstance")
            val crashlyticsInstance = getInstanceMethod.invoke(null)
            val recordExceptionMethod = crashlyticsClass.getMethod("recordException", Throwable::class.java)
            recordExceptionMethod.invoke(crashlyticsInstance, throwable)
        } catch (e: Exception) {
            Log.e("FirebaseCrashReporter", "Failed to report crash via reflection", e)
        }
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        updateCrashlyticsState()
    }

    private fun updateCrashlyticsState() {
        try {
            val crashlyticsClass = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstanceMethod = crashlyticsClass.getMethod("getInstance")
            val crashlyticsInstance = getInstanceMethod.invoke(null)
            val setCollectionEnabledMethod = crashlyticsClass.getMethod("setCrashlyticsCollectionEnabled", Boolean::class.java)
            setCollectionEnabledMethod.invoke(crashlyticsInstance, isEnabled)
            Log.i("FirebaseCrashReporter", "Firebase Crashlytics collection enabled: $isEnabled")
        } catch (e: Exception) {
            Log.i("FirebaseCrashReporter", "Firebase Crashlytics not present or failed to configure: ${e.message}")
        }
    }
}
