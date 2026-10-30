package com.inscopelabs.buildengine.util

/**
 * The log redaction gate.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §8: root URLs, signatures, base64 payloads, file content and secrets
 * must never appear in logs, metrics, traces or error responses. The capability's
 * signature and nonce join that forbidden set. This mirrors the gateway's
 * src/audit/redaction.ts — forbidden patterns are replaced, not filtered.
 */
object Redactor {

    /** Patterns whose presence in a log line is forbidden. */
    val FORBIDDEN_PATTERNS: List<String> = listOf(
        "ctxop-v1",
        "signature=",
        "nonce=",
        "authorization:",
        "bearer ",
    )

    fun redact(text: String): String = TODO("SCAFFOLD: replace every forbidden pattern.")

    fun containsForbidden(text: String): Boolean =
        TODO("SCAFFOLD: report whether a line would be rejected by the gate.")
}
