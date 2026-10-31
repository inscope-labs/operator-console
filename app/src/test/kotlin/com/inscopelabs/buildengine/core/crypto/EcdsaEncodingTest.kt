package com.inscopelabs.buildengine.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

class EcdsaEncodingTest {

    @Test
    fun `real ECDSA P-256 signatures convert to raw 64 bytes and back`() {
        val kpg = KeyPairGenerator.getInstance("EC").apply {
            initialize(256)
        }
        val keyPair = kpg.generateKeyPair()
        val message = "ctxop-v1\nk1\n1761840000\norder-1\natlas\ntask-1\nPUBLISH\ntask-1\n9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d\n7f3c9b1e5d2a7046".toByteArray(Charsets.UTF_8)

        val signer = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(message)
        }
        val derSig = signer.sign()

        val raw64 = EcdsaEncoding.derToP1363(derSig)
        assertEquals(64, raw64.size)

        val reconstructedDer = EcdsaEncoding.p1363ToDer(raw64)

        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyPair.public)
            update(message)
        }
        assertTrue("Reconstructed DER signature must verify against public key", verifier.verify(reconstructedDer))
    }

    @Test
    fun `derToP1363 handles integers with high bit set requiring zero stripping`() {
        // Construct DER signature where r and s have MSB >= 0x80 (thus 33 bytes in DER with 0x00 prefix)
        val r = ByteArray(32) { 0xFF.toByte() }
        val s = ByteArray(32) { 0x80.toByte() }

        val raw = ByteArray(64).apply {
            System.arraycopy(r, 0, this, 0, 32)
            System.arraycopy(s, 0, this, 32, 32)
        }

        val der = EcdsaEncoding.p1363ToDer(raw)
        // Check that DER contains 0x02 0x21 0x00 for each component
        assertTrue(der[2] == 0x02.toByte())
        assertEquals(33, der[3].toInt()) // r length is 33
        assertEquals(0x00.toByte(), der[4])

        val convertedBack = EcdsaEncoding.derToP1363(der)
        assertArrayEquals(raw, convertedBack)
    }

    @Test
    fun `derToP1363 handles integers with leading zeroes requiring zero padding`() {
        // Construct raw signature where r starts with leading zero (31 significant bytes)
        val raw = ByteArray(64)
        raw[0] = 0x00
        raw[1] = 0x42
        for (i in 2..31) raw[i] = 0x01
        for (i in 32..63) raw[i] = 0x02

        val der = EcdsaEncoding.p1363ToDer(raw)
        val convertedBack = EcdsaEncoding.derToP1363(der)
        assertArrayEquals(raw, convertedBack)
    }

    @Test
    fun `derToP1363 rejects malformed or truncated inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            EcdsaEncoding.derToP1363(ByteArray(4)) // too short
        }
        assertThrows(IllegalArgumentException::class.java) {
            EcdsaEncoding.derToP1363(byteArrayOf(0x02, 0x04, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05)) // not sequence 0x30
        }
    }

    @Test
    fun `p1363ToDer rejects non 64-byte inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            EcdsaEncoding.p1363ToDer(ByteArray(63))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EcdsaEncoding.p1363ToDer(ByteArray(65))
        }
    }

    @Test
    fun `base64url helpers encode and decode correctly`() {
        val raw = ByteArray(64) { it.toByte() }
        val der = EcdsaEncoding.p1363ToDer(raw)
        val b64 = EcdsaEncoding.derToP1363Base64Url(der)
        assertEquals(Base64Url.encode(raw), b64)

        val recoveredDer = EcdsaEncoding.p1363Base64UrlToDer(b64)
        assertArrayEquals(der, recoveredDer)
    }
}
