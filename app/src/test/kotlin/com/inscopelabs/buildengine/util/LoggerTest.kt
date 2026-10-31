package com.inscopelabs.buildengine.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNull

class LoggerTest {

    @After
    fun tearDown() {
        Logger.logSink = null
    }

    @Test
    fun `logger routes messages through redactor`() {
        var capturedMessage = ""
        Logger.logSink = { _, _, msg, _ -> capturedMessage = msg }

        Logger.i("TestTag", "Calling with Authorization: Bearer token_secret_12345")
        assertFalse(capturedMessage.contains("token_secret_12345"))
        assertTrue(capturedMessage.contains("Authorization: Bearer [REDACTED]"))

        Logger.d("TestTag", "Token is ghp_12345678901234567890")
        assertFalse(capturedMessage.contains("ghp_12345678901234567890"))
        assertTrue(capturedMessage.contains("ghp_[REDACTED]"))

        Logger.w("TestTag", "signature=abcde1234567890123456789012345678901234567890")
        assertTrue(capturedMessage.contains("signature=[REDACTED]"))

        val error = RuntimeException("Sensitive bearer my_token failed")
        Logger.e("TestTag", "Error occurred", error)
        assertTrue(capturedMessage.contains("Error occurred"))
    }

    @Test
    fun `exception messages and causes are redacted before reaching the sink`() {
        var captured: Throwable? = null
        Logger.logSink = { _, _, _, error -> captured = error }
        val cause = IllegalStateException("inner nonce=7f3c9b1e5d2a7046deadbeef")
        val error = IllegalArgumentException("bad header Authorization: Bearer super_secret_token_value", cause)

        Logger.e("TestTag", "failed", error)

        val sanitized = captured!!
        assertFalse(sanitized === error)
        assertFalse(sanitized.message!!.contains("super_secret_token_value"))
        assertTrue(sanitized.message!!.contains(IllegalArgumentException::class.java.name))
        assertFalse(sanitized.cause!!.message!!.contains("7f3c9b1e5d2a7046deadbeef"))
    }

    @Test
    fun `a missing error stays missing`() {
        var called = false
        var captured: Throwable? = RuntimeException("sentinel")
        Logger.logSink = { _, _, _, error -> called = true; captured = error }

        Logger.e("TestTag", "no throwable")

        assertTrue(called)
        assertNull(captured)
    }
}
