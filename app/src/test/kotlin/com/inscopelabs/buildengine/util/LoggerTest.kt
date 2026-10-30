package com.inscopelabs.buildengine.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
