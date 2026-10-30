package com.inscopelabs.buildengine.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the log redaction gate: a capability signature, a nonce and a credential
 * must never survive into a log line.
 */
class RedactorTest {

    @Test
    fun `forbidden set covers the capability material`() {
        val patterns = Redactor.FORBIDDEN_PATTERNS
        assertTrue(patterns.any { it.contains("signature") })
        assertTrue(patterns.any { it.contains("nonce") })
        assertTrue(patterns.any { it.contains("ctxop-v1") })
        assertTrue(patterns.any { it.contains("bearer") })
        assertTrue(patterns.any { it.contains("ghp_") })
    }

    @Test
    fun `bearer tokens and authorization headers are replaced`() {
        val raw = "GET /plans HTTP/1.1\nAuthorization: Bearer op_cred_9823472938472934\nHost: gateway"
        val redacted = Redactor.redact(raw)
        assertFalse(redacted.contains("op_cred_9823472938472934"))
        assertTrue(redacted.contains("Authorization: Bearer [REDACTED]"))

        val inlineBearer = "User logged in with bearer secret_token_xyz"
        val redactedInline = Redactor.redact(inlineBearer)
        assertFalse(redactedInline.contains("secret_token_xyz"))
        assertTrue(redactedInline.contains("bearer [REDACTED]"))
    }

    @Test
    fun `github tokens are replaced`() {
        val raw = "Cloning using token ghp_abcdefghijklmnopqrstuvwxyz123456"
        val redacted = Redactor.redact(raw)
        assertFalse(redacted.contains("abcdefghijklmnopqrstuvwxyz123456"))
        assertTrue(redacted.contains("ghp_[REDACTED]"))

        val rawPat = "Using token github_pat_11AAAAAAA000000000000000000000000"
        val redactedPat = Redactor.redact(rawPat)
        assertFalse(redactedPat.contains("11AAAAAAA000000000000000000000000"))
        assertTrue(redactedPat.contains("github_pat_[REDACTED]"))
    }

    @Test
    fun `signatures and nonces are replaced`() {
        val rawSig = "Posting request with signature=MEQCIG7x8k1m2n3o4p5q6r7s8t9u0v1w2x3y4z5a6b7c8d9e0f1"
        val redactedSig = Redactor.redact(rawSig)
        assertFalse(redactedSig.contains("MEQCIG7x8k1m2n3o4p5q6r7s8t9u0v1w2x3y4z5a6b7c8d9e0f1"))
        assertTrue(redactedSig.contains("signature=[REDACTED]"))

        val rawNonce = "Generated nonce=7f3c9b1e5d2a704689abcdef"
        val redactedNonce = Redactor.redact(rawNonce)
        assertFalse(redactedNonce.contains("7f3c9b1e5d2a704689abcdef"))
        assertTrue(redactedNonce.contains("nonce=[REDACTED]"))
    }

    @Test
    fun `json capability payload is replaced`() {
        val json = """{"plan":"atlas","task":"1","capability":"ctxop-v1\nk2\n1761840000..."}"""
        val redacted = Redactor.redact(json)
        assertFalse(redacted.contains("ctxop-v1"))
        assertTrue(redacted.contains("\"capability\": \"[REDACTED]\""))
    }

    @Test
    fun `generic secrets are replaced`() {
        val logLine = "Connecting with secret=mySuperSecretKey123 and apiKey=apiKey987654"
        val redacted = Redactor.redact(logLine)
        assertFalse(redacted.contains("mySuperSecretKey123"))
        assertFalse(redacted.contains("apiKey987654"))
        assertTrue(redacted.contains("secret=[REDACTED]"))
        assertTrue(redacted.contains("apiKey=[REDACTED]"))
    }

    @Test
    fun `containsForbidden accurately flags sensitive content`() {
        assertTrue(Redactor.containsForbidden("Authorization: Bearer mytoken"))
        assertTrue(Redactor.containsForbidden("token ghp_12345678901234567890"))
        assertTrue(Redactor.containsForbidden("ctxop-v1"))
        assertTrue(Redactor.containsForbidden("signature=xyz1234567890123456789012345678901234567890"))
        assertFalse(Redactor.containsForbidden("Normal log line: plan atlas task 10008 started"))
    }
}
