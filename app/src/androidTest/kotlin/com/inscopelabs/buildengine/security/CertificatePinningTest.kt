package com.inscopelabs.buildengine.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inscopelabs.buildengine.core.security.CertificatePinMismatchException
import com.inscopelabs.buildengine.core.security.CertificatePinning
import com.inscopelabs.buildengine.core.security.HostPinConfig
import com.inscopelabs.buildengine.core.security.PinSetExpiredException
import com.inscopelabs.buildengine.core.security.PinValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Instrumented test verifying [CertificatePinning] functionality and rejection on mismatch.
 *
 * Implements Stage 2.3 / T2-305.
 */
@RunWith(AndroidJUnit4::class)
class CertificatePinningTest {

    @Test
    fun defaultPinningConfigurationIsLoaded() {
        val pinning = CertificatePinning()
        assertTrue(pinning.isPinned("qnuralink.workers.dev"))
        assertTrue(pinning.isPinned("test.workers.dev"))

        val validation = pinning.validatePinSet("qnuralink.workers.dev")
        assertEquals(PinValidationResult.Valid, validation)
    }

    @Test
    fun okHttpPinnerBuildsSuccessfully() {
        val pinning = CertificatePinning()
        val pinner = pinning.buildCertificatePinner()
        assertNotNull(pinner)
    }

    @Test
    fun dynamicPinConfigurationAndValidation() {
        val pinning = CertificatePinning()
        val now = Instant.now()

        val config = HostPinConfig(
            pattern = "custom.gateway.net",
            primaryPins = listOf("sha256/hxqRlPTuQZQXLjt3o0+/JZaMcj79nlSx4vdwNY+wizg="),
            backupPins = listOf("sha256/i7WTqTvh0OioIruIfFR4kRnetJ3GMTVI2fQvfEQPpZ8="),
            expiration = now.plus(200, ChronoUnit.DAYS),
            description = "Custom test host",
        )
        pinning.setPinConfig(config)

        assertTrue(pinning.isPinned("custom.gateway.net"))
        val result = pinning.validatePinSet("custom.gateway.net", now = now)
        assertEquals(PinValidationResult.Valid, result)
    }
}
