package com.inscopelabs.buildengine.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun `success result behaves as expected`() {
        val result: AppResult<String> = AppResult.Success("hello")
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("hello", result.getOrNull())
        assertEquals("hello", result.getOrThrow())

        val mapped = result.map { it.length }
        assertTrue(mapped is AppResult.Success)
        assertEquals(5, (mapped as AppResult.Success).value)

        val flatMapped = result.flatMap { AppResult.Success(it.uppercase()) }
        assertEquals("HELLO", flatMapped.getOrNull())
    }

    @Test
    fun `failure result behaves as expected`() {
        val result: AppResult<String> = AppResult.Failure("ERR_CODE", "Something failed")
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())

        assertThrows(IllegalStateException::class.java) {
            result.getOrThrow()
        }

        val mapped = result.map { it.length }
        assertTrue(mapped is AppResult.Failure)
        assertEquals("ERR_CODE", (mapped as AppResult.Failure).code)

        val folded = result.fold(
            onSuccess = { "success" },
            onFailure = { code, msg -> "$code: $msg" },
        )
        assertEquals("ERR_CODE: Something failed", folded)
    }
}
