package com.inscopelabs.buildengine.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Covers the log redaction gate: a capability signature, a nonce and a credential
 * must never survive into a log line.
 *
 * The write gateway's ADR 0004 requires this to be tested by injecting sentinel
 * secrets and asserting they never appear in captured output. Every test here is
 * of that shape: a sentinel goes in, and the assertion is that it does not come out.
 */
class RedactorTest {

    @Test
    fun `forbidden set covers the capability material`() {
        val patterns = Redactor.FORBIDDEN_PATTERNS
        assertTrue(patterns.any { it.contains("signature") })
        assertTrue(patterns.any { it.contains("nonce") })
        assertTrue(patterns.any { it.contains("ctxop-v1") })
    }

    @Test
    fun `every declared pattern is removed from a line`() {
        for (pattern in Redactor.FORBIDDEN_PATTERNS) {
            val line = "before $pattern after"

            val redacted = Redactor.redact(line)

            assertFalse("'$pattern' survived redaction: '$redacted'", redacted.contains(pattern))
            assertTrue("'$pattern' was not reported as forbidden", Redactor.containsForbidden(line))
        }
    }

    @Test
    fun `a capability signature does not survive`() {
        val line = "minted signature=$SIGNATURE"

        val redacted = Redactor.redact(line)

        assertFalse(redacted.contains(SIGNATURE))
        assertFalse(Redactor.containsForbidden(redacted))
    }

    @Test
    fun `a bare capability signature does not survive`() {
        // A signature logged without a label is still a payload, and the payload
        // rule is what catches it.
        val redacted = Redactor.redact("minted $SIGNATURE")

        assertFalse(redacted.contains(SIGNATURE))
    }

    @Test
    fun `a capability nonce does not survive`() {
        val line = "minted nonce=$NONCE"

        val redacted = Redactor.redact(line)

        assertFalse(redacted.contains(NONCE))
        assertFalse(Redactor.containsForbidden(redacted))
    }

    @Test
    fun `a bare nonce does not survive`() {
        // A nonce logged without a label must still be caught: it is replay
        // protection, and a replay is exactly what it defends against.
        val redacted = Redactor.redact("minted $NONCE for task-10008")

        assertFalse(redacted.contains(NONCE))
    }

    @Test
    fun `a whole capability message does not survive`() {
        val redacted = Redactor.redact("minted capability=$CAPABILITY_MESSAGE")

        assertFalse(redacted.contains("ctxop-v1"))
        assertFalse(redacted.contains("order-7731"))
        assertFalse(redacted.contains("7f3c9b1e5d2a7046"))
        assertFalse(Redactor.containsForbidden(redacted))
    }

    @Test
    fun `a bearer credential does not survive`() {
        val redacted = Redactor.redact("calling gateway with bearer $OPERATOR_CREDENTIAL")

        assertFalse(redacted.contains(OPERATOR_CREDENTIAL))
    }

    @Test
    fun `an authorization header does not survive`() {
        val redacted = Redactor.redact("Authorization: Bearer $OPERATOR_CREDENTIAL")

        assertFalse(redacted.contains(OPERATOR_CREDENTIAL))
    }

    @Test
    fun `a GitHub token does not survive`() {
        for (token in listOf(GITHUB_PAT, GITHUB_FINE_GRAINED_PAT, AWS_ACCESS_KEY_ID)) {
            val redacted = Redactor.redact("found $token in a submission")

            assertFalse("'$token' survived redaction", redacted.contains(token))
        }
    }

    @Test
    fun `a PEM private key block does not survive`() {
        val redacted = Redactor.redact("dumping $PEM_PRIVATE_KEY")

        assertFalse(redacted.contains("PRIVATE KEY-----"))
        assertFalse(redacted.contains("MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ"))
    }

    @Test
    fun `the root URL of the gateway does not survive`() {
        val redacted = Redactor.redact("calling https://gateway.example.com/plans")

        assertFalse(redacted.contains("gateway.example.com"))
    }

    @Test
    fun `a query string does not survive`() {
        val redacted = Redactor.redact("calling https://gateway.example.com/plans?plan=atlas&token=abc")

        assertFalse(redacted.contains("plan=atlas"))
        assertFalse(redacted.contains("token=abc"))
    }

    @Test
    fun `the head SHA stays legible`() {
        // The head SHA is shown to the operator on screen and is not a secret. It
        // has to stay in the log too, or a head-drift failure — V7 — cannot be
        // diagnosed.
        val line = "head $HEAD_SHA matches the work order"

        assertEquals(line, Redactor.redact(line))
    }

    @Test
    fun `an ordinary operational line is left alone`() {
        val line = "published task-10008-refactor-parser in 412ms"

        assertEquals(line, Redactor.redact(line))
    }

    @Test
    fun `redaction is idempotent`() {
        val lines = listOf(
            "minted signature=$SIGNATURE",
            "minted nonce=$NONCE",
            "minted capability=$CAPABILITY_MESSAGE",
            "calling gateway with bearer $OPERATOR_CREDENTIAL",
            "found $GITHUB_PAT in a submission",
            "dumping $PEM_PRIVATE_KEY",
            "calling https://gateway.example.com/plans?plan=atlas",
        )

        for (line in lines) {
            val once = Redactor.redact(line)
            val twice = Redactor.redact(once)

            assertEquals("redaction was not idempotent for '$line'", once, twice)
            assertFalse("a redacted line still holds forbidden material", Redactor.containsForbidden(once))
        }
    }

    @Test
    fun `containsForbidden agrees with redact`() {
        val forbidden = listOf(
            "ctxop-v1",
            "signature=$SIGNATURE",
            "nonce=$NONCE",
            "bearer $OPERATOR_CREDENTIAL",
            "Authorization: Bearer $OPERATOR_CREDENTIAL",
            "$GITHUB_PAT",
            "$GITHUB_FINE_GRAINED_PAT",
            "$AWS_ACCESS_KEY_ID",
            "-----BEGIN PRIVATE KEY-----",
            "https://gateway.example.com",
        )
        val allowed = listOf(
            "",
            "published task-10008-refactor-parser in 412ms",
            "head $HEAD_SHA matches the work order",
            "capability expired after 300s",
        )

        for (line in forbidden) {
            assertTrue("'$line' was not reported as forbidden", Redactor.containsForbidden(line))
        }
        for (line in allowed) {
            assertFalse("'$line' was wrongly reported as forbidden", Redactor.containsForbidden(line))
        }
    }

    private companion object {
        /** A 32-byte HMAC-SHA256 signature, base64url-encoded without padding. */
        val SIGNATURE: String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(ByteArray(32) { (it * 7 + 3).toByte() })

        /** The nonce from the worked example in docs/capability-format.md §4. */
        const val NONCE: String = "7f3c9b1e5d2a7046"

        /** A full, 40-character git object id. */
        const val HEAD_SHA: String = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"

        /** The capability from the worked example in docs/capability-format.md §4. */
        val CAPABILITY_MESSAGE: String = """
            ctxop-v1
            k2
            1761840000
            order-7731
            atlas
            10008
            PUBLISH
            task-10008-refactor-parser
            $HEAD_SHA
            $NONCE
        """.trimIndent()

        const val OPERATOR_CREDENTIAL: String = "oc_live_7Qf2mZ8xR4vB1nT6wK3yH5dJ0"

        const val GITHUB_PAT: String = "ghp_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"

        const val GITHUB_FINE_GRAINED_PAT: String =
            "github_pat_11AA0Bb1Cc2Dd3Ee4Ff5Gg6Hh7Ii8Jj9Kk0Ll1Mm2Nn3"

        const val AWS_ACCESS_KEY_ID: String = "AKIAIOSFODNN7EXAMPLE"

        val PEM_PRIVATE_KEY: String = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ
            -----END PRIVATE KEY-----
        """.trimIndent()
    }
}
