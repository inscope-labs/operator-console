package com.inscopelabs.buildengine.capability.signing

import com.inscopelabs.buildengine.core.crypto.Base64Url
import com.inscopelabs.buildengine.core.crypto.EcdsaEncoding
import com.inscopelabs.buildengine.core.crypto.KeystoreSigner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

/**
 * Covers both signing directions behind the CapabilitySigner seam: Model B (the
 * console signs with the device key) and Model A (HMAC / Lab mode).
 */
class CapabilitySignerTest {

    @Test
    fun `both directions implement the signer contract`() {
        val signers: List<CapabilitySigner> = listOf(
            DeviceKeyCapabilitySigner(),
            HmacCapabilitySigner(),
        )
        assertEquals(2, signers.size)
        assertEquals(
            listOf(DeviceKeyCapabilitySigner::class, HmacCapabilitySigner::class),
            signers.map { it::class },
        )
    }

    @Test
    fun `hmac signer produces deterministic HMAC-SHA256 signatures`() {
        val secret = "test-secret-key-32-chars-long!".toByteArray(Charsets.UTF_8)
        val signer = HmacCapabilitySigner(keyId = "lab-k1", secret = secret)
        assertEquals("lab-k1", signer.kid())

        val message = "ctxop-v1\nlab-k1\n1761840000\norder-1\natlas\ntask-1\nPUBLISH\ntask-1\n9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d\n7f3c9b1e5d2a7046".toByteArray(Charsets.UTF_8)
        val sig1 = signer.sign(message)
        val sig2 = signer.sign(message)

        assertEquals(32, sig1.size)
        assertArrayEquals(sig1, sig2)

        val b64 = signer.signBase64Url(message)
        assertEquals(Base64Url.encode(sig1), b64)

        // Different secret produces different signature
        val signerOther = HmacCapabilitySigner(keyId = "lab-k1", secret = "other-secret-key-32-chars-long".toByteArray(Charsets.UTF_8))
        val sigOther = signerOther.sign(message)
        assertNotEquals(b64, Base64Url.encode(sigOther))
    }

    @Test
    fun `device key capability signer delegates to keystore signer with injected signature`() {
        val kpg = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        val keyPair = kpg.generateKeyPair()

        val sigEngine = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
        }

        val keystoreSigner = KeystoreSigner(
            kidProvider = { "k2" },
            signatureProvider = { sigEngine },
        )
        val deviceSigner = DeviceKeyCapabilitySigner(keystoreSigner)

        assertEquals("k2", deviceSigner.kid())

        val message = "test-message-bytes".toByteArray(Charsets.UTF_8)
        val rawSig = deviceSigner.sign(message)

        assertEquals(64, rawSig.size)

        // Verify the signature against the public key
        val derSig = EcdsaEncoding.p1363ToDer(rawSig)
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyPair.public)
            update(message)
        }
        assertTrue("Signature produced by DeviceKeyCapabilitySigner must verify", verifier.verify(derSig))
    }
}
