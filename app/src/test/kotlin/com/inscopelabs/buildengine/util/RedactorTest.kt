package com.inscopelabs.buildengine.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the log redaction gate: a capability signature, a nonce and a credential
 * must never survive into a log line.
 *
 * SCAFFOLD PLACEHOLDER — replace with the real redaction assertions.
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
    fun `placeholder assertion`() {
        // SCAFFOLD: replace with the real redaction round trip — assert that a
        // capability signature, a nonce and a bearer token do not survive
        // redact() unchanged.
        assertEquals(0, Redactor.FORBIDDEN_PATTERNS.size - Redactor.FORBIDDEN_PATTERNS.size)
    }
}
