package com.inscopelabs.buildengine.core.security

import com.inscopelabs.buildengine.core.time.FixedClock
import io.mockk.every
import io.mockk.mockk
import okhttp3.CertificatePinner
import okhttp3.Handshake
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * JVM unit tests for [CertificatePinning] (Stage 2.3 / T2-305).
 *
 * Verifies:
 * - Match against primary pin
 * - Match against backup pin (BD-2.1)
 * - Mismatch rejection with [CertificatePinMismatchException]
 * - Expired pin set rejection with [PinSetExpiredException] (BD-2.2)
 * - Empty pin set rejection in release mode with [PinSetEmptyException]
 * - Localhost bypass in debug mode
 * - BD-2.2 180-day release rule and 60-day warning threshold
 * - Dynamic pin set rotation at runtime
 * - OkHttp [CertificatePinner] and [Interceptor] integration
 */
class CertificatePinningTest {

    private lateinit var fixedClock: FixedClock
    private val baselineTime: Instant = Instant.parse("2026-11-08T00:00:00Z")

    private lateinit var primaryCert: X509Certificate
    private lateinit var backupCert: X509Certificate
    private lateinit var unknownCert: X509Certificate

    private lateinit var primaryPin: String
    private lateinit var backupPin: String
    private lateinit var unknownPin: String

    @Before
    fun setUp() {
        fixedClock = FixedClock(baselineTime.epochSecond)

        primaryCert = createMockCertificate("primary-ca-key-bytes-1".toByteArray())
        backupCert = createMockCertificate("backup-ca-key-bytes-2".toByteArray())
        unknownCert = createMockCertificate("untrusted-ca-key-bytes-3".toByteArray())

        primaryPin = CertificatePinning.calculateSpkiPin(primaryCert)
        backupPin = CertificatePinning.calculateSpkiPin(backupCert)
        unknownPin = CertificatePinning.calculateSpkiPin(unknownCert)
    }

    private fun createMockCertificate(keyBytes: ByteArray): X509Certificate {
        val cert = mockk<X509Certificate>(relaxed = true)
        val pubKey = mockk<PublicKey>()
        every { pubKey.encoded } returns keyBytes
        every { cert.publicKey } returns pubKey
        return cert
    }

    private fun createTestPinning(
        isRelease: Boolean = true,
        pattern: String = "gateway.example.com",
        expiration: Instant = baselineTime.plus(300, ChronoUnit.DAYS),
    ): CertificatePinning {
        val config = HostPinConfig(
            pattern = pattern,
            primaryPins = listOf(primaryPin),
            backupPins = listOf(backupPin),
            expiration = expiration,
            description = "Test Pin Set",
        )
        return CertificatePinning(
            clock = fixedClock,
            isReleaseMode = isRelease,
            initialConfigs = listOf(config),
        )
    }

    @Test
    fun matchPrimaryPin_allowsConnection() {
        val pinning = createTestPinning()
        // Peer certificates presented: [primaryCert]
        pinning.check("gateway.example.com", listOf(primaryCert))
        // If no exception is thrown, verification passed
        assertTrue(pinning.isPinned("gateway.example.com"))
    }

    @Test
    fun matchBackupPin_allowsConnection() {
        val pinning = createTestPinning()
        // Primary certificate absent; only backup certificate presented
        pinning.check("gateway.example.com", listOf(backupCert))
        // Verification succeeds with backup pin
        assertTrue(pinning.isPinned("gateway.example.com"))
    }

    @Test
    fun mismatchPin_throwsCertificatePinMismatchException() {
        val pinning = createTestPinning()
        try {
            pinning.check("gateway.example.com", listOf(unknownCert))
            fail("Expected CertificatePinMismatchException")
        } catch (e: CertificatePinMismatchException) {
            assertTrue(e.message?.contains("Certificate pinning failure") == true)
            assertTrue(e.message?.contains("gateway.example.com") == true)
        }
    }

    @Test
    fun emptyPeerCertificates_throwsCertificatePinMismatchException() {
        val pinning = createTestPinning()
        try {
            pinning.check("gateway.example.com", emptyList())
            fail("Expected CertificatePinMismatchException for empty chain")
        } catch (e: CertificatePinMismatchException) {
            assertTrue(e.message?.contains("chain is empty") == true)
        }
    }

    @Test
    fun expiredPinSet_throwsPinSetExpiredException() {
        // Pin set expired 10 days ago relative to fixedClock
        val expiredTime = baselineTime.minus(10, ChronoUnit.DAYS)
        val pinning = createTestPinning(expiration = expiredTime)

        try {
            // Even though certificate pin matches primary pin, expired set must fail closed
            pinning.check("gateway.example.com", listOf(primaryCert))
            fail("Expected PinSetExpiredException")
        } catch (e: PinSetExpiredException) {
            assertTrue(e.message?.contains("expired on") == true)
        }
    }

    @Test
    fun releaseMode_emptyPins_throwsPinSetEmptyException() {
        val pinning = CertificatePinning(
            clock = fixedClock,
            isReleaseMode = true,
            initialConfigs = emptyList(),
        )

        try {
            pinning.check("unpinned.host.example.com", listOf(primaryCert))
            fail("Expected PinSetEmptyException in release mode")
        } catch (e: PinSetEmptyException) {
            assertTrue(e.message?.contains("Certificate pinning is required in release mode") == true)
        }
    }

    @Test
    fun debugMode_localhost_exemptFromPinning() {
        val pinning = CertificatePinning(
            clock = fixedClock,
            isReleaseMode = false,
            initialConfigs = emptyList(),
        )

        // Local loopback hosts must not throw exceptions in debug mode
        pinning.check("10.0.2.2", emptyList())
        pinning.check("localhost", emptyList())
        pinning.check("127.0.0.1", emptyList())
    }

    @Test
    fun validatePinSet_bd2_2_insufficientLifetimeUnder180DaysInRelease() {
        // 170 days remaining (< 180 days required per BD-2.2)
        val nearExpiry = baselineTime.plus(170, ChronoUnit.DAYS)
        val pinning = createTestPinning(isRelease = true, expiration = nearExpiry)

        val result = pinning.validatePinSet("gateway.example.com")
        assertTrue(result is PinValidationResult.InsufficientLifetime)
        val failure = result as PinValidationResult.InsufficientLifetime
        assertEquals(170L, failure.daysRemaining)
        assertEquals(180L, failure.requiredDays)
    }

    @Test
    fun validatePinSet_bd2_2_warningAt60Days() {
        // 45 days remaining (<= 60 days warning threshold)
        val nearExpiry = baselineTime.plus(45, ChronoUnit.DAYS)
        val pinning = createTestPinning(isRelease = false, expiration = nearExpiry)

        val result = pinning.validatePinSet("gateway.example.com")
        assertTrue(result is PinValidationResult.Warning)
        val warning = result as PinValidationResult.Warning
        assertEquals(45L, warning.daysRemaining)
    }

    @Test
    fun validatePinSet_validWhenSufficientLifetime() {
        // 250 days remaining (> 180 days)
        val safeExpiry = baselineTime.plus(250, ChronoUnit.DAYS)
        val pinning = createTestPinning(isRelease = true, expiration = safeExpiry)

        val result = pinning.validatePinSet("gateway.example.com")
        assertEquals(PinValidationResult.Valid, result)
    }

    @Test
    fun validatePinSet_reportsExpiredWhenClockPassesExpiration() {
        val pastExpiry = baselineTime.minus(1, ChronoUnit.DAYS)
        val pinning = createTestPinning(expiration = pastExpiry)

        val result = pinning.validatePinSet("gateway.example.com")
        assertTrue(result is PinValidationResult.Expired)
    }

    @Test
    fun validatePinSet_reportsNotPinnedWhenNoHostConfig() {
        val pinning = createTestPinning()
        val result = pinning.validatePinSet("unknown.host.org")
        assertTrue(result is PinValidationResult.NotPinned)
    }

    @Test
    fun dynamicRotation_allowsUpdatingPinsAtRuntime() {
        val pinning = createTestPinning()

        // Initially, unknownCert fails
        try {
            pinning.check("gateway.example.com", listOf(unknownCert))
            fail("Expected mismatch before rotation")
        } catch (_: CertificatePinMismatchException) {
        }

        // Dynamically rotate pin set to add unknownCert as new backup pin
        val rotatedConfig = HostPinConfig(
            pattern = "gateway.example.com",
            primaryPins = listOf(primaryPin),
            backupPins = listOf(backupPin, unknownPin),
            expiration = baselineTime.plus(300, ChronoUnit.DAYS),
            description = "Rotated Pin Set",
        )
        pinning.setPinConfig(rotatedConfig)

        // Now unknownCert is accepted without restarting or updating app
        pinning.check("gateway.example.com", listOf(unknownCert))
        assertTrue(pinning.pinsFor("gateway.example.com").contains(unknownPin))
    }

    @Test
    fun wildcardHostMatching_matchesSubdomains() {
        val pinning = createTestPinning(pattern = "*.workers.dev")

        assertTrue(pinning.isPinned("test.workers.dev"))
        assertTrue(pinning.isPinned("qnuralink.workers.dev"))
        assertTrue(pinning.isPinned("sub.staging.workers.dev"))

        // Peer certificate check passes against wildcard
        pinning.check("staging.qnuralink.workers.dev", listOf(primaryCert))
    }

    @Test
    fun buildCertificatePinner_constructsOkHttpPinner() {
        val pinning = createTestPinning()
        val okHttpPinner = pinning.buildCertificatePinner()
        assertNotNull(okHttpPinner)

        // Verify OkHttp's native check passes for primary cert
        okHttpPinner.check("gateway.example.com", listOf(primaryCert))

        // Verify OkHttp's native check passes for backup cert
        okHttpPinner.check("gateway.example.com", listOf(backupCert))

        // Verify OkHttp's native check rejects untrusted cert
        try {
            okHttpPinner.check("gateway.example.com", listOf(unknownCert))
            fail("Expected OkHttp SSLPeerUnverifiedException")
        } catch (e: Exception) {
            assertTrue(e is javax.net.ssl.SSLPeerUnverifiedException)
        }
    }

    @Test
    fun interceptor_enforcesExpiryAndPinValidation() {
        val pinning = createTestPinning()
        val interceptor = pinning.asInterceptor()

        // 1. Valid request with TLS handshake containing primaryCert
        val validRequest = Request.Builder()
            .url("https://gateway.example.com/health")
            .build()

        val mockHandshake = mockk<Handshake>()
        every { mockHandshake.peerCertificates } returns listOf(primaryCert)

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns validRequest

        val mockResponse = Response.Builder()
            .request(validRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .handshake(mockHandshake)
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        every { chain.proceed(validRequest) } returns mockResponse

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)

        // 2. Interceptor fails when handshake presents untrusted certificate
        val invalidHandshake = mockk<Handshake>()
        every { invalidHandshake.peerCertificates } returns listOf(unknownCert)
        val mismatchResponse = mockResponse.newBuilder()
            .handshake(invalidHandshake)
            .build()
        every { chain.proceed(validRequest) } returns mismatchResponse

        try {
            interceptor.intercept(chain)
            fail("Expected CertificatePinMismatchException from interceptor")
        } catch (e: CertificatePinMismatchException) {
            assertTrue(e.message?.contains("Certificate pinning failure") == true)
        }
    }

    @Test
    fun defaultConfigs_complyWithBd2_1AndBd2_2() {
        val pinning = CertificatePinning(clock = fixedClock, isReleaseMode = true)

        // Default configs must contain *.workers.dev and qnuralink.workers.dev
        assertTrue(pinning.isPinned("any.workers.dev"))
        assertTrue(pinning.isPinned("qnuralink.workers.dev"))

        val workersDevConfig = pinning.getPinConfig("foo.workers.dev")
        assertNotNull(workersDevConfig)
        assertTrue(workersDevConfig!!.primaryPins.isNotEmpty())
        assertTrue(workersDevConfig.backupPins.isNotEmpty())

        // Must satisfy BD-2.2 (at least 180 days remaining from baseline date)
        val validation = pinning.validatePinSet("qnuralink.workers.dev")
        assertEquals(PinValidationResult.Valid, validation)
    }
}
