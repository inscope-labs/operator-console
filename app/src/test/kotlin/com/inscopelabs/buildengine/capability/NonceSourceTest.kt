package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class NonceSourceTest {

    @Test
    fun `device nonce source produces valid hexadecimal nonces`() {
        val nonceSource = DeviceNonceSource(SecureRandomSource())
        val nonce = nonceSource.newNonce()
        assertEquals(32, nonce.length)
        assertTrue(nonce.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `device nonce source never repeats within a run even with collisions from source`() {
        // A fake random that produces the same sequence twice, then a new sequence
        var callCount = 0
        val fakeRandom = object : Random() {
            override fun nextBytes(bytes: ByteArray) {
                callCount++
                if (callCount <= 2) {
                    // First and second call produce identical bytes (collision)
                    bytes.fill(0xAA.toByte())
                } else {
                    // Third call produces distinct bytes
                    bytes.fill(0xBB.toByte())
                }
            }
        }

        val predictableSource = SecureRandomSource(fakeRandom)
        val nonceSource = DeviceNonceSource(predictableSource)

        val firstNonce = nonceSource.newNonce()
        val secondNonce = nonceSource.newNonce()

        assertNotEquals("Nonces must never repeat in a single run", firstNonce, secondNonce)
        assertEquals(3, callCount) // Verified collision retry
    }
}
