package com.inscopelabs.buildengine.util

import android.util.Log

/**
 * The redaction-aware logger.
 *
 * Design review §8: this is a hard gate for every log line. Nothing that reaches it
 * may contain a credential, a capability signature or a nonce. That includes the
 * message and cause chain of any [Throwable]: exception messages routinely embed the
 * value that was being parsed, so they are redacted exactly like log text.
 */
object Logger {

    internal var logSink: ((priority: Int, tag: String, message: String, error: Throwable?) -> Unit)? = null

    private const val MAX_CAUSE_DEPTH = 5

    fun d(tag: String, message: String) {
        val sanitized = Redactor.redact(message)
        logSink?.invoke(Log.DEBUG, tag, sanitized, null)
        try {
            Log.d(tag, sanitized)
        } catch (_: RuntimeException) {
            // android.util.Log is not available in plain JVM unit tests.
        }
    }

    fun i(tag: String, message: String) {
        val sanitized = Redactor.redact(message)
        logSink?.invoke(Log.INFO, tag, sanitized, null)
        try {
            Log.i(tag, sanitized)
        } catch (_: RuntimeException) {
        }
    }

    fun w(tag: String, message: String) {
        val sanitized = Redactor.redact(message)
        logSink?.invoke(Log.WARN, tag, sanitized, null)
        try {
            Log.w(tag, sanitized)
        } catch (_: RuntimeException) {
        }
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        val sanitized = Redactor.redact(message)
        val sanitizedError = error?.let { sanitize(it, 0) }
        logSink?.invoke(Log.ERROR, tag, sanitized, sanitizedError)
        try {
            if (sanitizedError != null) {
                Log.e(tag, sanitized, sanitizedError)
            } else {
                Log.e(tag, sanitized)
            }
        } catch (_: RuntimeException) {
        }
    }

    /** A copy of a throwable whose message and causes have been through [Redactor]. The original is never logged. */
    internal class RedactedThrowable(type: String, message: String?) :
        RuntimeException(if (message == null) type else "$type: $message")

    private fun sanitize(error: Throwable, depth: Int): Throwable {
        val copy = RedactedThrowable(error.javaClass.name, error.message?.let { Redactor.redact(it) })
        copy.stackTrace = error.stackTrace
        val cause = error.cause
        if (cause != null && cause !== error && depth < MAX_CAUSE_DEPTH) {
            copy.initCause(sanitize(cause, depth + 1))
        }
        return copy
    }
}
