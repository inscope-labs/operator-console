package com.inscopelabs.buildengine.testutil

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.Bundle
import java.io.File
import java.util.UUID

open class FakeContext(
    val fakePrefs: FakeSharedPreferences = FakeSharedPreferences(),
    val baseDir: File = File(System.getProperty("java.io.tmpdir"), "test-files-" + UUID.randomUUID())
) : ContextWrapper(null) {

    init {
        baseDir.mkdirs()
    }

    private val appInfo = ApplicationInfo().apply {
        flags = ApplicationInfo.FLAG_DEBUGGABLE
    }

    val startedActivities = mutableListOf<Intent>()

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        return fakePrefs
    }

    override fun getApplicationContext(): Context {
        return this
    }

    override fun getPackageName(): String {
        return "com.inscopelabs.buildengine"
    }

    override fun getApplicationInfo(): ApplicationInfo {
        return appInfo
    }

    override fun getFilesDir(): File {
        return baseDir
    }

    override fun startActivity(intent: Intent?) {
        intent?.let { startedActivities.add(it) }
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        intent?.let { startedActivities.add(it) }
    }
}

class FakeActivity(
    val fakeContext: FakeContext = FakeContext()
) : Activity() {

    val startedIntents = mutableListOf<Intent>()
    var isFinished = false
        private set

    override fun getApplicationContext(): Context = fakeContext

    override fun startActivity(intent: Intent?) {
        intent?.let { startedIntents.add(it) }
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        intent?.let { startedIntents.add(it) }
    }

    override fun finish() {
        isFinished = true
    }
}
