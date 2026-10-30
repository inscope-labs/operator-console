package com.inscopelabs.buildengine.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.security.CertificatePinning
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that a pin mismatch is rejected.
 *
 * SCAFFOLD PLACEHOLDER — replace with the real pin-mismatch assertions.
 */
@RunWith(AndroidJUnit4::class)
class CertificatePinningTest {

    @Test
    fun pinningIsAvailable() {
        assertNotNull(CertificatePinning())
    }
}
