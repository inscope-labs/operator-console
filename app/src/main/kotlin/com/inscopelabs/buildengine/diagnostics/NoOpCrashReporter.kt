package com.inscopelabs.buildengine.diagnostics

class NoOpCrashReporter : CrashReporter {
    override fun initialize() {}
    override fun reportCrash(thread: Thread, throwable: Throwable) {}
    override fun setEnabled(enabled: Boolean) {}
}
