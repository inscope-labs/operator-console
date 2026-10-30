package com.inscopelabs.buildengine.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The result type shared across the data and domain layers.
 *
 * The point of the type is that a failure carries a *stable code* as well as a
 * message: the gateway's V5–V8 refusals are four different situations and the UI
 * has to be able to tell them apart.
 */
class AppResultTest {

    @Test
    fun `a success is a success and carries its value`() {
        val result: AppResult<Int> = AppResult.Success(7)

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(7, result.getOrNull())
        assertNull(result.errorOrNull())
    }

    @Test
    fun `a failure carries a stable code and a message`() {
        val failure = AppResult.Failure(code = "scope_mismatch", message = "capability names task-A")
        val result: AppResult<Int> = failure

        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
        assertSame(failure, result.errorOrNull())
        assertEquals("scope_mismatch", failure.code)
    }

    @Test
    fun `map transforms the success value`() {
        val result = AppResult.Success("task-10008").map { it.length }

        assertEquals(AppResult.Success(10), result)
    }

    @Test
    fun `map leaves a failure untouched`() {
        val failure = AppResult.Failure(code = "replayed", message = "nonce already seen")
        val result: AppResult<Int> = failure.map { error: Throwable -> error.message!!.length }

        assertSame(failure, result)
    }

    @Test
    fun `mapFailure transforms the failure and leaves a success untouched`() {
        val failure = AppResult.Failure(code = "replayed", message = "nonce already seen")

        val remapped = failure.mapFailure { AppResult.Failure("v6_replay", it.message) }
        assertEquals("v6_replay", (remapped as AppResult.Failure).code)

        val success: AppResult<Int> = AppResult.Success(1)
        assertSame(success, success.mapFailure { AppResult.Failure("x", "y") })
    }

    @Test
    fun `mapCatching turns a thrown exception into a failure`() {
        val result = AppResult.Success(1).mapCatching { error("boom") }

        val failure = result as AppResult.Failure
        assertEquals(AppResult.CODE_UNEXPECTED, failure.code)
        assertEquals("boom", failure.message)
    }

    @Test
    fun `onSuccess and onFailure run on their own branch only`() {
        var successes = 0
        var failures = 0

        AppResult.Success(1)
            .onSuccess { successes++ }
            .onFailure { failures++ }
        assertEquals(1, successes)
        assertEquals(0, failures)

        AppResult.Failure(code = "expired", message = "ttl elapsed")
            .onSuccess { successes++ }
            .onFailure { failures++ }
        assertEquals(1, successes)
        assertEquals(1, failures)
    }

    @Test
    fun `getOrElse falls back to the failure branch`() {
        assertEquals(3, AppResult.Success(3).getOrElse { -1 })

        val failure: AppResult<Int> = AppResult.Failure(code = "expired", message = "ttl elapsed")
        assertEquals(-1, failure.getOrElse { -1 })
    }

    @Test
    fun `a failure held as a result still answers the value-shaped accessors`() {
        // `Failure` is an `AppResult<Nothing>`, so a member that returns the class's
        // own type parameter is specialised to `Void` in `Failure`. A non-null
        // fallback reached through such a member would fail with a
        // `ClassCastException` rather than with a wrong answer.
        val failure: AppResult<Int> = AppResult.Failure(code = "expired", message = "ttl elapsed")

        assertEquals(-1, failure.getOrElse { -1 })
        assertNull(failure.getOrNull())
        assertEquals("expired", failure.errorOrNull()?.code)
    }

    @Test
    fun `fold collapses both branches into one value`() {
        assertEquals("ok:3", AppResult.Success(3).fold({ "ok:$it" }, { "err:${it.code}" }))
        assertEquals(
            "err:expired",
            AppResult.Failure(code = "expired", message = "ttl elapsed").fold({ "ok:$it" }, { "err:${it.code}" }),
        )
    }

    @Test
    fun `a failure is usable wherever the result type is expected`() {
        // AppResult.Failure is an AppResult<Nothing>, so it satisfies AppResult<T>.
        val results: List<AppResult<Int>> = listOf(
            AppResult.Success(1),
            AppResult.Failure(code = "expired", message = "ttl elapsed"),
        )

        assertEquals(2, results.size)
        assertEquals(1, results.count { it.isSuccess })
    }
}
