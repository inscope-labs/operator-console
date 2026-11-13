package com.inscopelabs.buildengine.data.local

import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdempotencyKeyGeneratorTest {

    @Test
    fun `generated keys match contract 32-hex format and are unique`() {
        val generator = IdempotencyKeyGenerator(SecureRandomSource())
        val keys = (1..32).map { generator.generate() }

        keys.forEach { key ->
            assertTrue("Expected 32 lowercase hex chars, got '$key'", KEY_PATTERN.matches(key))
            assertTrue(IdempotencyKeyGenerator.isValid(key))
        }
        assertEquals(32, keys.toSet().size)
    }

    @Test
    fun `idempotency key is independent of and never equal to capability nonce`() {
        val seedA = 42L
        val seedB = 99L
        val nonceSource = DeviceNonceSource(SecureRandomSource(Random(seedA)))
        val keyGenerator = IdempotencyKeyGenerator(SecureRandomSource(Random(seedB)))

        val nonce = nonceSource.newNonce()
        val key = keyGenerator.generate()

        assertNotEquals(nonce, key)
        assertTrue(KEY_PATTERN.matches(key))
    }

    @Test
    fun `key validation rejects non-hex, uppercase, and wrong lengths`() {
        assertFalse(IdempotencyKeyGenerator.isValid(null))
        assertFalse(IdempotencyKeyGenerator.isValid(""))
        assertFalse(IdempotencyKeyGenerator.isValid("a".repeat(31)))
        assertFalse(IdempotencyKeyGenerator.isValid("a".repeat(33)))
        assertFalse(IdempotencyKeyGenerator.isValid("A".repeat(32)))
        assertFalse(IdempotencyKeyGenerator.isValid("g" + "0".repeat(31)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `OperationIntent rejects forbidden capability or secret substrings`() {
        OperationIntent(
            operation = OperationType.PUBLISH,
            plan = "atlas",
            task = "10008",
            branch = "task-10008-refactor-parser",
            expectedHead = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
            reason = "ctxop-v1\nk2\nsecret",
        )
    }

    private companion object {
        val KEY_PATTERN = Regex("^[0-9a-f]{32}$")
    }
}
