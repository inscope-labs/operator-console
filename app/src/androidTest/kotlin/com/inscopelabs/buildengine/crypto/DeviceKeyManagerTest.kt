package com.inscopelabs.buildengine.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.crypto.DeviceKeyManager
import com.inscopelabs.buildengine.core.crypto.DeviceKeySpec
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the device key exists, is non-exportable and requires user
 * authentication.
 *
 * SCAFFOLD PLACEHOLDER — replace with real Keystore assertions.
 */
@RunWith(AndroidJUnit4::class)
class DeviceKeyManagerTest {

    @Test
    fun keyManagerIsAvailable() {
        assertNotNull(DeviceKeyManager())
        assertNotNull(DeviceKeySpec(alias = "test-alias"))
    }
}
