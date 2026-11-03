package com.inscopelabs.abx.clipinbox.diagnostics

interface CrashReporter {
    fun initialize()
    fun reportCrash(thread: Thread, throwable: Throwable)
    fun setEnabled(enabled: Boolean)
}
