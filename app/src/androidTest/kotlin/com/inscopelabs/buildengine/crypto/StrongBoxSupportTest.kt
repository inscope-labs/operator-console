package com.inscopelabs.buildengine.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the posture report is honest on the device under test — including
 * on an emulator, where the Keystore is software-backed.
 *
 * SCAFFOLD PLACEHOLDER — replace with real posture assertions.
 */
@RunWith(AndroidJUnit4::class)
class StrongBoxSupportTest {

    @Test
    fun postureSupportIsAvailable() {
        assertNotNull(StrongBoxSupport())
    }
}
