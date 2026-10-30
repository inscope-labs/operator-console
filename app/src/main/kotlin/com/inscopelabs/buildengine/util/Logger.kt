package com.inscopelabs.buildengine.util

import android.util.Log

/**
 * The redaction-aware logger.
 *
 * Design review §8: this is a hard gate for every log line. Nothing that reaches it
 * may contain a credential, a capability signature or a nonce.
 *
 * Every public method funnels through the single private [write], so there is no
 * path through this object that logs without passing [Redactor] first — the gate
 * cannot be bypassed by calling a differently-named method. The tag is redacted
 * too: it is normally a compile-time constant, but a tag is still a string that
 * reaches a sink.
 *
 * The throwable is redacted as well as the message. A stack trace carries the
 * exception's message, and an exception raised while parsing a capability or an
 * `Authorization` header carries the offending value with it.
 */
object Logger {

    fun d(tag: String, message: String): Unit = write(Log.DEBUG, tag, message, null)

    fun i(tag: String, message: String): Unit = write(Log.INFO, tag, message, null)

    fun w(tag: String, message: String): Unit = write(Log.WARN, tag, message, null)

    fun e(tag: String, message: String, error: Throwable? = null): Unit =
        write(Log.ERROR, tag, message, error)

    private fun write(level: Int, tag: String, message: String, error: Throwable?) {
        val safeTag = Redactor.redact(tag)
        val safeMessage = Redactor.redact(message)
        val safeError = error?.let { Redactor.redact(it.stackTraceToString()) }

        val line = if (safeError == null) {
            safeMessage
        } else {
            buildString {
                append(safeMessage)
                append('\n')
                append(safeError)
            }
        }

        Log.println(level, safeTag, line)
    }
}
