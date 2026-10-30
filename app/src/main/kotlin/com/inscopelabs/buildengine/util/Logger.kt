package com.inscopelabs.buildengine.util

/**
 * The redaction-aware logger.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §8: this is a hard gate for every log line. Nothing that reaches it
 * may contain a credential, a capability signature or a nonce.
 */
object Logger {

    fun d(tag: String, message: String): Unit = TODO("SCAFFOLD: redact, then log.")

    fun i(tag: String, message: String): Unit = TODO("SCAFFOLD: redact, then log.")

    fun w(tag: String, message: String): Unit = TODO("SCAFFOLD: redact, then log.")

    fun e(tag: String, message: String, error: Throwable? = null): Unit =
        TODO("SCAFFOLD: redact, then log.")
}
