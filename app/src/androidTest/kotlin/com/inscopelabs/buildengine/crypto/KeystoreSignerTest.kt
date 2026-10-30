package com.inscopelabs.buildengine.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.crypto.KeystoreSigner
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Signs a message and verifies it with the enrolled public key.
 *
 * SCAFFOLD PLACEHOLDER — replace with the real sign-and-verify assertions.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSignerTest {

    @Test
    fun signerIsAvailable() {
        assertNotNull(KeystoreSigner())
    }
}
