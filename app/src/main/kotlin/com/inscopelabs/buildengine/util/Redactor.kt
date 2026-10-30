package com.inscopelabs.buildengine.util

/**
 * The log redaction gate.
 *
 * Design review §8: root URLs, signatures, base64 payloads, file content and secrets
 * must never appear in logs, metrics, traces or error responses. The capability's
 * signature and nonce join that forbidden set. This mirrors the gateway's
 * src/audit/redaction.ts — forbidden patterns are replaced, not filtered.
 *
 * **Replaced, not filtered.** A filtered line is a line that goes missing, and a
 * missing line is indistinguishable from a line that was never produced — the
 * failure mode this gate exists to prevent is a secret reaching a sink, and a
 * silently dropped line hides only the symptom of that. Replacing the fragment
 * keeps the event and its shape while removing the material.
 *
 * The credential shapes are the ones the write gateway already enforces on its own
 * side (build-engine `release/forbidden.txt`); the capability shapes are the ones
 * this app is the only holder of.
 *
 * ### The rules, in the order they are applied
 *
 * 1. **PEM private key blocks** — the whole block, then the header, then the bare marker.
 * 2. **Credential shapes** — the GitHub token prefixes and the AWS access key id.
 * 3. **The capability message** — the domain separator plus the nine fields that
 *    follow it, so that a capability logged whole loses its nonce with it.
 * 4. **The capability itself** — the bare `ctxop-v1` domain separator.
 * 5. **Labelled secrets** — an `Authorization` header, a bearer credential, and the
 *    `signature=` / `nonce=` assignments, plus the generic `token=` / `secret=` /
 *    `password=` / `api_key=` / `credential=` shapes so that a credential the console
 *    was never meant to hold is still caught.
 * 6. **Root URLs and query strings** — the origin of an absolute URL is the
 *    deployment's identity and is never logged; the path is kept because it is the
 *    operation, not a secret. A query string is removed whole, because a query
 *    string can carry anything.
 * 7. **Hex runs** — a standalone lowercase-hex run of 16 characters or more is a
 *    nonce or a digest, so it is removed. The one exception is a 40-character run:
 *    that is a git object id, which the operator is shown on screen, which is not
 *    secret, and which has to stay legible for a head-drift failure — V7 — to be
 *    diagnosable.
 * 8. **base64url payloads** — a run of 32 or more base64url characters that carries
 *    an uppercase letter, a lowercase letter and a digit is encoded material (a
 *    32-byte signature encodes to 43 characters), so it is removed. The
 *    mixed-case-and-digit requirement is what keeps ordinary identifiers — branch
 *    names, class names — out of the marker.
 */
object Redactor {

    /** What a forbidden fragment is replaced with. */
    const val MARKER: String = "[REDACTED]"

    /** Patterns whose presence in a log line is forbidden. */
    val FORBIDDEN_PATTERNS: List<String> = listOf(
        "ctxop-v1",       // the capability domain separator
        "signature=",     // a capability signature
        "nonce=",         // a capability nonce
        "authorization:", // an Authorization header
        "bearer ",        // a bearer credential
        "ghp_",           // a GitHub token
        "github_pat_",    // a GitHub fine-grained token
        "AKIA",           // an AWS access key id
        "-----BEGIN",     // a PEM private key block
    )

    /**
     * The rules applied to a line, in order. Each one is replaced by [MARKER].
     *
     * Every marker in [FORBIDDEN_PATTERNS] is matched by at least one of these, so
     * that [containsForbidden] and [redact] cannot disagree about what is forbidden.
     */
    private val RULES: List<Regex> = listOf(
        // -- PEM private key material --
        Regex("-----BEGIN[ \\tA-Z]*PRIVATE KEY-----(?s:.*?)-----END[ \\tA-Z]*PRIVATE KEY-----"),
        Regex("-----BEGIN[ \\tA-Z]*PRIVATE KEY-----"),
        Regex("-----BEGIN[ \\tA-Z]*"),

        // -- credential shapes, mirroring the build engine's own secret scanner --
        Regex("github_pat_[A-Za-z0-9_]*"),
        Regex("gh[pousr]_[A-Za-z0-9]*"),
        Regex("AKIA[0-9A-Z]*"),

        // -- the capability message, whole: the separator plus its nine fields --
        Regex("ctxop-v1(?:\\n[^\\n]*){9}"),

        // -- the capability itself --
        Regex("ctxop-v1", RegexOption.IGNORE_CASE),

        // -- labelled secrets --
        Regex("authorization\\s*:[^\\r\\n]*", RegexOption.IGNORE_CASE),
        Regex("bearer\\s+[^\\s]*", RegexOption.IGNORE_CASE),
        Regex("signature\\s*[=:]\\s*[^\\s]*", RegexOption.IGNORE_CASE),
        Regex("nonce\\s*[=:]\\s*[^\\s]*", RegexOption.IGNORE_CASE),
        Regex(
            "(?:token|secret|password|passwd|api[-_]?key|credential)\\s*[=:]\\s*[^\\s]*",
            RegexOption.IGNORE_CASE,
        ),

        // -- root URLs and query strings --
        Regex("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s/?#]+"),
        Regex("\\?[^\\s]*"),
    )

    /** A standalone lowercase-hex run: a nonce or a digest, and long enough to be one. */
    private val HEX_RUN = Regex("(?<![0-9a-fA-F])[0-9a-f]{16,}(?![0-9a-fA-F])")

    /** A full git object id — the head SHA, which is not a secret. */
    private val GIT_OBJECT_ID = Regex("^[0-9a-f]{40}$")

    /** A base64url run long enough to be a payload rather than a word. */
    private val BASE64URL_PAYLOAD = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{32,}(?![A-Za-z0-9_-])")

    /**
     * Replaces every forbidden fragment in [text] with [MARKER].
     *
     * The result never contains a forbidden fragment, so `redact` is idempotent and
     * [containsForbidden] on the result is always `false`.
     */
    fun redact(text: String): String {
        var redacted = text
        for (rule in RULES) {
            redacted = rule.replace(redacted, MARKER)
        }
        redacted = HEX_RUN.replace(redacted) { match ->
            if (GIT_OBJECT_ID.matches(match.value)) match.value else MARKER
        }
        return BASE64URL_PAYLOAD.replace(redacted) { match ->
            if (looksEncoded(match.value)) MARKER else match.value
        }
    }

    /**
     * Reports whether a line would be rejected by the gate.
     *
     * This is the predicate behind [redact]: a line for which this is `true` has at
     * least one forbidden fragment, and redaction removes every one of them. The
     * two exemptions — the git object id and the identifier that merely looks
     * encoded — are applied here too, so the gate never rejects a line it would
     * leave alone.
     */
    fun containsForbidden(text: String): Boolean =
        RULES.any { it.containsMatchIn(text) } ||
            HEX_RUN.findAll(text).any { !GIT_OBJECT_ID.matches(it.value) } ||
            BASE64URL_PAYLOAD.findAll(text).any { looksEncoded(it.value) }

    /**
     * Whether a base64url run is encoded material rather than an identifier.
     *
     * Randomly generated bytes land in all three character classes, so requiring an
     * uppercase letter, a lowercase letter and a digit separates a payload from a
     * branch name or a class name. A 32-byte payload satisfies all three with
     * probability better than 1 in 1000.
     */
    private fun looksEncoded(value: String): Boolean {
        var upper = false
        var lower = false
        var digit = false
        for (character in value) {
            when {
                character in 'A'..'Z' -> upper = true
                character in 'a'..'z' -> lower = true
                character in '0'..'9' -> digit = true
            }
        }
        return upper && lower && digit
    }
}
