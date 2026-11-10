package com.inscopelabs.buildengine.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.inscopelabs.buildengine.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught exception handler.
 *
 * Responsibilities:
 * - Log every uncaught Java/Kotlin exception to logcat.
 * - Persist crash reports to internal storage safely with log rotation (local redacted diagnostics).
 * - Chained to Android's default exception handler to ensure standard system crash behavior.
 */
class GlobalExceptionHandler(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    companion object {
        const val CRASH_LOG_FILE = "crash_logs.txt"
        const val MAX_LOG_SIZE = 5L * 1024L * 1024L // 5 MB

        private val fileLock = Any()
    }

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    private val appContext: Context = context.applicationContext

    internal var processTerminator: () -> Unit = {
        try {
            Thread.sleep(300) // give the startActivity() IPC time to land before we self-terminate
        } catch (_: InterruptedException) {}
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess()
    }

    internal fun isDebugBuild(): Boolean {
        return try {
            (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Throwable) {
            true
        }
    }

    internal fun getVersionName(): String {
        return try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }
    }

    internal fun getVersionCode(): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode.toLong()
            }
        } catch (_: Throwable) {
            1L
        }
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable
    ) {
        try {
            val referenceCode = generateReferenceCode()
            val crashReport = buildCrashReport(thread, throwable, referenceCode)

            // Log crash structured to our diagnostic logger
            Logger.e("XTOOLS_CRASH", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)

            // Persist the crash report to crash_logs.txt (for error screens)
            writeCrashLog(crashReport)

            launchErrorActivity(throwable, crashReport, referenceCode)
        } catch (e: Exception) {
            try {
                Log.e("XTOOLS_CRASH", "Exception inside exception handler", e)
            } catch (ignored: Throwable) {}
        }

        val startedCrashActivity = crashActivityLaunched
        if (!startedCrashActivity) {
            defaultHandler?.uncaughtException(thread, throwable)
        }

        processTerminator()
    }

    @Volatile
    internal var crashActivityLaunched = false

    internal fun generateReferenceCode(): String =
        "XTOOLS-" + java.lang.Long.toString(System.currentTimeMillis(), 36).uppercase(Locale.US)

    internal fun launchErrorActivity(throwable: Throwable, fullReport: String, referenceCode: String) {
        val isDebug = isDebugBuild()
        val intent = if (isDebug) {
            val metadata = buildString {
                appendLine("Manufacturer: ${Build.MANUFACTURER}")
                appendLine("Model: ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                append("App Version: ${getVersionName()} (${getVersionCode()})")
            }
            val stackWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stackWriter))

            Intent(appContext, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(CrashActivity.EXTRA_EXCEPTION_TYPE, throwable.javaClass.name)
                putExtra(CrashActivity.EXTRA_MESSAGE, throwable.message ?: "No message")
                putExtra(CrashActivity.EXTRA_METADATA, metadata)
                putExtra(CrashActivity.EXTRA_STACK_TRACE, stackWriter.toString())
                putExtra(CrashActivity.EXTRA_FULL_REPORT, fullReport)
            }
        } else {
            Intent(appContext, UserFacingErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(UserFacingErrorActivity.EXTRA_REFERENCE_CODE, referenceCode)
                putExtra(UserFacingErrorActivity.EXTRA_FULL_REPORT, fullReport)
            }
        }
        appContext.startActivity(intent)
        crashActivityLaunched = true
    }

    @Suppress("DEPRECATION")
    internal fun buildCrashReport(
        thread: Thread,
        throwable: Throwable,
        referenceCode: String
    ): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))

        val isDebug = isDebugBuild()
        val versionName = getVersionName()
        val versionCode = getVersionCode()

        return buildString {
            appendLine("====================================================")
            appendLine("GLOBAL CRASH REPORT")
            appendLine("====================================================")

            appendLine("Reference Code : $referenceCode")
            appendLine("Timestamp      : ${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.US
                ).format(Date())
            }")

            appendLine("Thread         : ${thread.name}")
            appendLine("Thread ID      : ${thread.id}")

            appendLine()
            appendLine("APPLICATION")

            appendLine("Package        : ${appContext.packageName}")
            appendLine("Version Name   : $versionName")
            appendLine("Version Code   : $versionCode")
            appendLine("Debug Build    : $isDebug")

            appendLine()
            appendLine("DEVICE")

            appendLine("Manufacturer   : ${Build.MANUFACTURER}")
            appendLine("Brand          : ${Build.BRAND}")
            appendLine("Model          : ${Build.MODEL}")
            appendLine("Device         : ${Build.DEVICE}")
            appendLine("Product        : ${Build.PRODUCT}")

            appendLine()
            appendLine("ANDROID")

            appendLine("Release        : ${Build.VERSION.RELEASE}")
            appendLine("SDK            : ${Build.VERSION.SDK_INT}")
            appendLine("Codename       : ${Build.VERSION.CODENAME}")

            appendLine()
            appendLine("EXCEPTION")

            appendLine("Type           : ${throwable.javaClass.name}")
            appendLine("Message        : ${throwable.message}")

            appendLine()
            appendLine("STACK TRACE")
            appendLine(writer.toString())

            var cause = throwable.cause
            while (cause != null) {
                appendLine()
                appendLine("CAUSED BY")
                appendLine(Log.getStackTraceString(cause))
                cause = cause.cause
            }

            appendLine("====================================================")
            appendLine()
        }
    }

    internal fun writeCrashLog(report: String) {
        synchronized(fileLock) {
            try {
                val file = File(appContext.filesDir, CRASH_LOG_FILE)
                
                if (file.exists() && (file.length() + report.length > MAX_LOG_SIZE)) {
                    val rotatedFile = File(appContext.filesDir, "$CRASH_LOG_FILE.1")
                    if (rotatedFile.exists()) {
                        rotatedFile.delete()
                    }
                    file.renameTo(rotatedFile)
                }

                FileOutputStream(file, true).bufferedWriter().use { writer ->
                    writer.write(report)
                    writer.flush()
                }
            } catch (e: Exception) {
                try {
                    Log.e("XTOOLS_CRASH", "Failed to write crash log to file", e)
                } catch (ignored: Throwable) {}
            }
        }
    }

    private fun exitProcess() {
        kotlin.system.exitProcess(10)
    }
}
