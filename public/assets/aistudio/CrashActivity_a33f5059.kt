package com.inscopelabs.abx.clipinbox.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.inscopelabs.abx.clipinbox.R

/**
 * On-screen crash report shown immediately after an uncaught exception,
 * launched by GlobalExceptionHandler instead of letting the OS show the
 * bare "App has stopped" dialog. Mirrors RecoveryActivity's structure
 * (same layout skeleton, same Copy/Restart pattern) but for runtime
 * crashes rather than startup-stage failures — deliberately kept as a
 * separate activity since the two failure classes shouldn't share
 * BootGuard's startup-only state.
 *
 * Reads everything from Intent extras rather than a shared store, since
 * this activity is started from inside the crash handler itself and must
 * not depend on anything that could also be in a broken state.
 */
class CrashActivity : ComponentActivity() {
    private companion object {
        const val TAG = "XTOOLS_CRASH_UI"

        const val EXTRA_EXCEPTION_TYPE = "extra_exception_type"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_METADATA = "extra_metadata"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_FULL_REPORT = "extra_full_report"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_crash)
            setupUI()
        } catch (t: Throwable) {
            // If even this screen fails to render, never let that become a
            // second crash — degrade to a Toast and finish, same fallback
            // RecoveryActivity uses.
            Log.e(TAG, "Failed to initialize CrashActivity UI safely", t)
            try {
                Toast.makeText(this, getString(R.string.crash_fallback_toast), Toast.LENGTH_LONG).show()
            } catch (ignored: Throwable) {}
            finish()
        }
    }

    private fun setupUI() {
        val tvExceptionType: TextView = findViewById(R.id.tvExceptionType)
        val tvMessage: TextView = findViewById(R.id.tvMessage)
        val tvMetadata: TextView = findViewById(R.id.tvMetadata)
        val tvStackTrace: TextView = findViewById(R.id.tvStackTrace)
        val btnCopy: Button = findViewById(R.id.btnCopy)
        val btnRestart: Button = findViewById(R.id.btnRestart)

        val exceptionType = intent.getStringExtra(EXTRA_EXCEPTION_TYPE)
            ?: getString(R.string.crash_unknown_type)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: getString(R.string.crash_unknown_message)
        val metadata = intent.getStringExtra(EXTRA_METADATA) ?: ""
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: ""
        val fullReport = intent.getStringExtra(EXTRA_FULL_REPORT) ?: ""

        tvExceptionType.text = exceptionType
        tvMessage.text = message
        tvMetadata.text = metadata
        tvStackTrace.text = stackTrace

        btnCopy.setOnClickListener {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ABX Crash Report", fullReport)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to copy crash report", t)
                Toast.makeText(this, getString(R.string.crash_copy_failed), Toast.LENGTH_SHORT).show()
            }
        }

        btnRestart.setOnClickListener {
            try {
                val relaunch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (relaunch != null) {
                    startActivity(relaunch)
                }
                finishAffinity()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to restart application", t)
                Toast.makeText(this, getString(R.string.crash_restart_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
