package com.inscopelabs.buildengine.crypto

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.crypto.StrongBoxSupport
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PoC task 32. The posture report must be honest on the device under test, including an emulator,
 * where the Keystore is software-backed. The assertions are relationships that hold on any device,
 * not a fixed expectation of hardware: a software key must never be reported as hardware-backed.
 */
@RunWith(AndroidJUnit4::class)
class StrongBoxSupportTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val alias = "poc-test-strongbox-posture"
    private val support = StrongBoxSupport(context)

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
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.apply {
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }

    @Test
    fun securityLevelIsOneOfTheKnownValues() {
        val known = setOf("SOFTWARE", "TRUSTED_ENVIRONMENT", "STRONGBOX", "UNKNOWN_SECURE")
        assertTrue("Unexpected security level ${support.securityLevel(alias)}", support.securityLevel(alias) in known)
    }

    @Test
    fun hardwareBackedAgreesWithSecurityLevel() {
        val hardwareLevels = setOf("TRUSTED_ENVIRONMENT", "STRONGBOX")
        assertEquals(
            "isHardwareBacked must be true exactly when the level is a hardware level",
            support.securityLevel(alias) in hardwareLevels,
            support.isHardwareBacked(alias),
        )
    }

    @Test
    fun aSoftwareKeyIsNeverReportedAsHardwareBacked() {
        if (support.securityLevel(alias) == "SOFTWARE") {
            assertFalse(support.isHardwareBacked(alias))
        }
    }

    @Test
    fun unknownAliasIsReportedAsUnknownAndNotHardwareBacked() {
        val missing = "poc-test-no-such-alias"
        assertEquals("UNKNOWN", support.securityLevel(missing))
        assertFalse(support.isHardwareBacked(missing))
    }

    @Test
    fun strongBoxAvailabilityMatchesTheSystemFeature() {
        val expected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        assertEquals(expected, support.isStrongBoxAvailable())
    }
}
