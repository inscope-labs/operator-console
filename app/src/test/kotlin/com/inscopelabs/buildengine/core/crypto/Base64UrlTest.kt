package com.inscopelabs.buildengine.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The base64url encoding is part of the `ctxop-v1` wire contract
 * (docs/capability-format.md §5): the URL-safe alphabet, no padding.
 */
class Base64UrlTest {

    @Test
    fun `encodes the RFC 4648 vectors without padding`() {
        assertEquals("", Base64Url.encode(ByteArray(0)))
        assertEquals("Zg", Base64Url.encode("f".toByteArray()))
        assertEquals("Zm8", Base64Url.encode("fo".toByteArray()))
        assertEquals("Zm9v", Base64Url.encode("foo".toByteArray()))
        assertEquals("Zm9vYg", Base64Url.encode("foob".toByteArray()))
        assertEquals("Zm9vYmE", Base64Url.encode("fooba".toByteArray()))
        assertEquals("Zm9vYmFy", Base64Url.encode("foobar".toByteArray()))
    }

    @Test
    fun `uses the URL-safe alphabet rather than the standard one`() {
        // 0xFB 0xFF encodes to "+/8=" in standard base64.
        val encoded = Base64Url.encode(byteArrayOf(0xFB.toByte(), 0xFF.toByte()))

        assertEquals("-_8", encoded)
        assertTrue("the URL-safe alphabet must not contain '+'", !encoded.contains('+'))
        assertTrue("the URL-safe alphabet must not contain '/'", !encoded.contains('/'))
    }

    @Test
    fun `never emits padding`() {
        for (size in 0..8) {
            val encoded = Base64Url.encode(ByteArray(size) { it.toByte() })

            assertTrue("padding in '$encoded'", !encoded.contains('='))
        }
    }

    @Test
    fun `round-trips arbitrary bytes`() {
        val random = kotlin.random.Random(seed = 20261030)

        repeat(64) {
            val original = ByteArray(random.nextInt(0, 96)) { random.nextInt().toByte() }

            val decoded = Base64Url.decode(Base64Url.encode(original))

            assertArrayEquals(original, decoded)
        }
    }

    @Test
    fun `decodes the RFC 4648 vectors`() {
        assertArrayEquals(ByteArray(0), Base64Url.decode(""))
        assertArrayEquals("f".toByteArray(), Base64Url.decode("Zg"))
        assertArrayEquals("fo".toByteArray(), Base64Url.decode("Zm8"))
        assertArrayEquals("foo".toByteArray(), Base64Url.decode("Zm9v"))
        assertArrayEquals("foob".toByteArray(), Base64Url.decode("Zm9vYg"))
        assertArrayEquals("fooba".toByteArray(), Base64Url.decode("Zm9vYmE"))
        assertArrayEquals("foobar".toByteArray(), Base64Url.decode("Zm9vYmFy"))
    }

    @Test
    fun `refuses padded input`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("Zg==")
        }

        assertTrue(error.message!!.contains("base64url"))
    }

    @Test
    fun `refuses the standard base64 alphabet`() {
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("+/8")
        }
    }

    @Test
    fun `refuses a length that cannot be base64`() {
        // A base64 body is never one character short of a multiple of four.
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("Z")
        }
    }

    @Test
    fun `refuses characters outside the alphabet`() {
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("Zm9v!")
        }
    }
}
