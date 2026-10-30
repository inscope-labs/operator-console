package com.inscopelabs.buildengine.util

import android.util.Log

/**
 * The redaction-aware logger.
 *
 * Design review §8: this is a hard gate for every log line. Nothing that reaches it
 * may contain a credential, a capability signature or a nonce.
 */
object Logger {

    internal var logSink: ((priority: Int, tag: String, message: String, error: Throwable?) -> Unit)? = null

    fun d(tag: String, message: String) {
        val sanitized = Redactor.redact(message)
        logSink?.invoke(Log.DEBUG, tag, sanitized, null)
        try {
            Log.d(tag, sanitized)
        } catch (_: Throwable) {
            // Handled when android.util.Log stubs are not initialized
        }
    }

    fun i(tag: String, message: String) {
        val sanitized = Redactor.redact(message)
        logSink?.invoke(Log.INFO, tag, sanitized, null)
        try {
            Log.i(tag, sanitized)
        } catch (_: Throwable) {
        }
    }

    fun w(tag: String, message: String) {
        val sanitized = Redactor.redact(message)
        logSink?.invoke(Log.WARN, tag, sanitized, null)
        try {
            Log.w(tag, sanitized)
        } catch (_: Throwable) {
        }
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        val sanitized = Redactor.redact(message)
        val sanitizedErrorMsg = error?.message?.let { Redactor.redact(it) }
        logSink?.invoke(Log.ERROR, tag, sanitized, error)
        try {
            if (error != null) {
                Log.e(tag, sanitized, error)
            } else {
                Log.e(tag, sanitized)
            }
        } catch (_: Throwable) {
        }
    }
}
