package com.inscopelabs.buildengine.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.crypto.EcdsaEncoding
import com.inscopelabs.buildengine.core.crypto.KeystoreSigner
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PoC task 31. Signs a message with a real Keystore key through [KeystoreSigner] and verifies it
 * with the matching public key. The key needs no user authentication, so it runs on an emulator;
 * the biometric-bound device key is covered by the device checklist, not here.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSignerTest {

    private val alias = "poc-test-keystore-signer"
    private val message = "ctxop-v1\nk9\n1761840000\norder-1\nplan-1\n10008\nPUBLISH\ntask-10008-x\n" +
        "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d\n7f3c9b1e5d2a7046"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun newSignature(): Signature = Signature.getInstance("SHA256withECDSA").apply {
        initSign(keyStore().getKey(alias, null) as PrivateKey)
    }

    @Before
    fun createKey() {
        deleteKey()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
        }.generateKeyPair()
    }

    @After
    fun deleteKey() {
        keyStore().apply { if (containsAlias(alias)) deleteEntry(alias) }
    }

    @Test
    fun signatureIsRawP1363OfSixtyFourBytesAndVerifiesWithTheEnrolledPublicKey() {
        val signer = KeystoreSigner("k9", newSignature())
        val raw = signer.sign(message.toByteArray(Charsets.UTF_8))

        assertEquals("secp256r1 r||s must be 64 bytes", 64, raw.size)

        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyStore().getCertificate(alias).publicKey)
            update(message.toByteArray(Charsets.UTF_8))
        }
        assertTrue("signature must verify with the public key", verifier.verify(EcdsaEncoding.p1363ToDer(raw)))
    }

    @Test
    fun aTamperedMessageDoesNotVerify() {
        val raw = KeystoreSigner("k9", newSignature()).sign(message.toByteArray(Charsets.UTF_8))
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(keyStore().getCertificate(alias).publicKey)
            update((message + "x").toByteArray(Charsets.UTF_8))
        }
        assertFalse(verifier.verify(EcdsaEncoding.p1363ToDer(raw)))
    }

    @Test
    fun kidIsTheOneTheSignerWasBuiltWith() {
        assertEquals("k9", KeystoreSigner("k9", newSignature()).kid())
    }

    @Test
    fun signingFailsClosedWhenNoAuthorisedSignatureEngineIsAvailable() {
        val failure = runCatching { KeystoreSigner().sign(message.toByteArray(Charsets.UTF_8)) }.exceptionOrNull()
        assertTrue("expected GeneralSecurityException, got $failure", failure is GeneralSecurityException)
    }

    @Test
    fun p1363RoundTripsThroughDer() {
        val raw = KeystoreSigner("k9", newSignature()).sign(message.toByteArray(Charsets.UTF_8))
        assertArrayEquals(raw, EcdsaEncoding.derToP1363(EcdsaEncoding.p1363ToDer(raw)))
    }
}
