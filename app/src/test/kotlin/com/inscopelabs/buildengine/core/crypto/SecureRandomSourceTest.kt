package com.inscopelabs.buildengine.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureRandomSourceTest {

    @Test
    fun `nextNonce produces at least 32 lowercase hex characters`() {
        val source = SecureRandomSource()
        val nonce = source.nextNonce()
        assertEquals(32, nonce.length)
        assertTrue(nonce.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `consecutive nonces are distinct`() {
        val source = SecureRandomSource()
        val n1 = source.nextNonce()
        val n2 = source.nextNonce()
        assertNotEquals(n1, n2)
    }

    @Test
    fun `nextBytes produces requested size`() {
        val source = SecureRandomSource()
        val bytes = source.nextBytes(24)
        assertEquals(24, bytes.size)
    }
}
