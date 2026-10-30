package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
