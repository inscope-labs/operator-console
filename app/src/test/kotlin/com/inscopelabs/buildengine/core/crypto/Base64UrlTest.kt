package com.inscopelabs.buildengine.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Base64UrlTest {

    @Test
    fun `encode produces no padding and uses url safe characters`() {
        val input = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xbf.toByte())
        val encoded = Base64Url.encode(input)
        assertFalse("Must not contain padding", encoded.contains("="))
        assertFalse("Must not contain standard base64 +", encoded.contains("+"))
        assertFalse("Must not contain standard base64 /", encoded.contains("/"))
        assertTrue("Must contain url safe chars", encoded.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' })
    }

    @Test
    fun `roundtrip encode and decode succeeds`() {
        val original = "ctxop-v1 test string with special bytes \u0000\u0001\u00FF".toByteArray(Charsets.UTF_8)
        val encoded = Base64Url.encode(original)
        val decoded = Base64Url.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `decode rejects padding characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("YWJj=")
        }
    }

    @Test
    fun `decode rejects plus and slash characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("YW+j")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("YW/j")
        }
    }

    @Test
    fun `decode rejects invalid characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("abc!123")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("abc 123")
        }
    }

    @Test
    fun `empty array encodes to empty string and decodes back`() {
        val encoded = Base64Url.encode(byteArrayOf())
        assertEquals("", encoded)
        val decoded = Base64Url.decode("")
        assertEquals(0, decoded.size)
    }

    @Test
    fun `decode rejects non canonical trailing bits`() {
        assertEquals(1, Base64Url.decode("QQ").size)
        assertThrows(IllegalArgumentException::class.java) {
            Base64Url.decode("QR")
        }
    }
}
