package com.inscopelabs.buildengine.util

/**
 * The log redaction gate.
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
        "ghp_",
        "github_pat_",
    )

    private val BEARER_AUTH_REGEX = Regex("""(?i)\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/-]+=*""")
    private val BEARER_PREFIX_REGEX = Regex("""(?i)\b(bearer\s+)[A-Za-z0-9._~+/-]+=*""")
    private val GHP_PAT_REGEX = Regex("""\bghp_[A-Za-z0-9_]{10,}""")
    private val GITHUB_PAT_REGEX = Regex("""\bgithub_pat_[A-Za-z0-9_]{10,}""")
    private val SIG_ASSIGN_REGEX = Regex("""(?i)\b(sig(?:nature)?\s*[:=]\s*)[A-Za-z0-9_-]{40,}""")
    private val NONCE_ASSIGN_REGEX = Regex("""(?i)\b(nonce\s*[:=]\s*)[A-Za-z0-9_-]{16,}""")
    private val JSON_SIG_REGEX = Regex("""(?i)"(signature|sig)"\s*:\s*"[^"]+"""")
    private val JSON_NONCE_REGEX = Regex("""(?i)"nonce"\s*:\s*"[^"]+"""")
    private val JSON_CAPABILITY_REGEX = Regex("""(?i)"capability"\s*:\s*"[^"]+"""")
    private val GENERIC_SECRET_REGEX = Regex("""(?i)\b(secret|password|credential|apiKey)\s*[:=]\s*([^\s,;]+)""")
    /**
     * A capability: the domain separator, the nine fields after it, and the optional
     * signature line that follows on the wire. The signature is matched together with
     * the message so it can never survive as a stray line after the redaction marker.
     */
    private val CTXOP_CAPABILITY_REGEX =
        Regex("""ctxop-v1(?:\r?\n[^\r\n]+){9}(?:\r?\n[A-Za-z0-9_-]{40,})?""")

    /** The same capability with literal backslash-n separators, as it appears inside JSON or log text. */
    private val CTXOP_ESCAPED_CAPABILITY_REGEX =
        Regex("""ctxop-v1(?:\\n[^\\\s"',;]+){9}(?:\\n[A-Za-z0-9_-]{40,})?""")

    fun redact(text: String): String {
        var result = text

        // Capability block
        result = CTXOP_CAPABILITY_REGEX.replace(result, "[REDACTED_CAPABILITY]")
        result = CTXOP_ESCAPED_CAPABILITY_REGEX.replace(result, "[REDACTED_CAPABILITY]")
        result = result.replace("ctxop-v1", "[REDACTED_DOMAIN]")

        // Bearer tokens & authorization headers
        result = BEARER_AUTH_REGEX.replace(result, "Authorization: Bearer [REDACTED]")
        result = BEARER_PREFIX_REGEX.replace(result, "$1[REDACTED]")

        // GitHub tokens
        result = GHP_PAT_REGEX.replace(result, "ghp_[REDACTED]")
        result = GITHUB_PAT_REGEX.replace(result, "github_pat_[REDACTED]")

        // JSON capability fields
        result = JSON_CAPABILITY_REGEX.replace(result, "\"capability\": \"[REDACTED]\"")
        result = JSON_SIG_REGEX.replace(result, "\"$1\": \"[REDACTED]\"")
        result = JSON_NONCE_REGEX.replace(result, "\"nonce\": \"[REDACTED]\"")

        // Signature & nonce assignments
        result = SIG_ASSIGN_REGEX.replace(result, "$1[REDACTED]")
        result = NONCE_ASSIGN_REGEX.replace(result, "$1[REDACTED]")

        // Generic secret parameters
        result = GENERIC_SECRET_REGEX.replace(result, "$1=[REDACTED]")

        return result
    }

    fun containsForbidden(text: String): Boolean {
        if (FORBIDDEN_PATTERNS.any { text.contains(it, ignoreCase = true) }) {
            return true
        }
        return BEARER_AUTH_REGEX.containsMatchIn(text) ||
            BEARER_PREFIX_REGEX.containsMatchIn(text) ||
            GHP_PAT_REGEX.containsMatchIn(text) ||
            GITHUB_PAT_REGEX.containsMatchIn(text) ||
            SIG_ASSIGN_REGEX.containsMatchIn(text) ||
            NONCE_ASSIGN_REGEX.containsMatchIn(text) ||
            JSON_SIG_REGEX.containsMatchIn(text) ||
            JSON_NONCE_REGEX.containsMatchIn(text) ||
            JSON_CAPABILITY_REGEX.containsMatchIn(text) ||
            GENERIC_SECRET_REGEX.containsMatchIn(text)
    }
}
