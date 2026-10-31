package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * The nonce is the console's contribution to replay protection: the gateway stores
 * it until the capability expires and refuses one it has already seen. It therefore
 * has to come from a cryptographically secure source and it has to differ every
 * time.
 */
class DeviceNonceSourceTest {

    @Test
    fun `the nonce is taken from the CSPRNG source`() {
        val source = SecureRandomSource(CountingSecureRandom())

        val nonce = DeviceNonceSource(source).newNonce()

        assertEquals("000102030405060708090a0b0c0d0e0f", nonce)
    }

    @Test
    fun `the nonce is lowercase hex of the expected length`() {
        val nonce = DeviceNonceSource(SecureRandomSource()).newNonce()

        assertTrue("nonce was '$nonce'", NONCE.matches(nonce))
        assertEquals(SecureRandomSource.NONCE_BYTES * 2, nonce.length)
    }

    @Test
    fun `two nonces are never the same`() {
        val source = DeviceNonceSource(SecureRandomSource())

        val first = source.newNonce()
        val second = source.newNonce()

        assertNotEquals(first, second)
    }

    @Test
    fun `consecutive nonces from one source differ`() {
        val source = DeviceNonceSource(SecureRandomSource(CountingSecureRandom()))

        assertNotEquals(source.newNonce(), source.newNonce())
    }

    @Test
    fun `a repeated value from the random source is retried`() {
        val source = DeviceNonceSource(SecureRandomSource(ScriptedSecureRandom(1, 1, 2)))
        assertEquals("01".repeat(16), source.newNonce())
        assertEquals("02".repeat(16), source.newNonce())
    }

    @Test
    fun `a random source that only repeats is refused rather than issuing a duplicate`() {
        val source = DeviceNonceSource(SecureRandomSource(ScriptedSecureRandom(7)))
        assertEquals("07".repeat(16), source.newNonce())
        assertThrows(IllegalStateException::class.java) { source.newNonce() }
    }

    private companion object {
        val NONCE = Regex("^[0-9a-f]+$")
    }
}

/**
 * A [SecureRandom] that hands out a known, incrementing byte sequence, so that the
 * nonce a source produces is exactly predictable.
 */
private class CountingSecureRandom : SecureRandom() {

    private var counter = 0

    override fun nextBytes(bytes: ByteArray) {
        for (index in bytes.indices) {
            bytes[index] = counter.toByte()
            counter = (counter + 1) and 0xFF
        }
    }
}

/** Fills every requested buffer with the next scripted byte value, repeating the last one once the script runs out. */
private class ScriptedSecureRandom(private vararg val fills: Int) : SecureRandom() {
    private var call = 0
    override fun nextBytes(bytes: ByteArray) {
        val value = fills[minOf(call, fills.size - 1)]
        call++
        bytes.fill(value.toByte())
    }
}
