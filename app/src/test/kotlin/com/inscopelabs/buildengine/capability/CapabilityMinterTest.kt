package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.core.crypto.Base64Url
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.time.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the capability TTL, nonce integration, minting lifecycle, and wire serialization.
 */
class CapabilityMinterTest {

    private val fixedTime = 1761840000L
    private val clock = FixedClock(fixedTime)
    private val signer = HmacCapabilitySigner(keyId = "k2", secretUtf8 = "secret-for-minter-test-32-bytes!")
    private val nonceSource = DeviceNonceSource(SecureRandomSource())
    private val minter = CapabilityMinter(
        signer = signer,
        nonceSource = nonceSource,
        clock = clock,
    )

    @Test
    fun `suggested ttl is five minutes (300 seconds)`() {
        assertEquals(300L, minter.ttlSeconds)
    }

    @Test
    fun `scope names the operation, the branch, the head and the nonce`() {
        val scope = CapabilityScope(
            op = OperationType.PUBLISH,
            branch = "task-10008-fix",
            head = "a".repeat(40),
            nonce = "7f3c9b1e5d2a7046",
        )
        assertEquals(OperationType.PUBLISH, scope.op)
        assertEquals("task-10008-fix", scope.branch)
        assertEquals(40, scope.head.length)
        assertEquals("7f3c9b1e5d2a7046", scope.nonce)
    }

    @Test
    fun `mint calculates expiry as now plus ttl from injected clock`() {
        val scope = CapabilityScope(
            op = OperationType.PUBLISH,
            branch = "task-10008-fix",
            head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
            nonce = "7f3c9b1e5d2a7046",
        )

        val signed = minter.mint(
            scope = scope,
            order = "order-7731",
            plan = "atlas",
            task = "10008",
        )

        assertEquals("k2", signed.capability.kid)
        assertEquals(fixedTime + 300L, signed.capability.exp)
        assertEquals("order-7731", signed.capability.order)
        assertEquals("atlas", signed.capability.plan)
        assertEquals("10008", signed.capability.task)
        assertEquals(OperationType.PUBLISH, signed.capability.op)
        assertEquals("task-10008-fix", signed.capability.branch)
        assertEquals("9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d", signed.capability.head)
        assertEquals("7f3c9b1e5d2a7046", signed.capability.nonce)
    }

    @Test
    fun `signed capability produces valid wire format and redacts in toString`() {
        val scope = CapabilityScope(
            op = OperationType.HALT,
            branch = "engine",
            head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
            nonce = "7f3c9b1e5d2a7046",
        )

        val signed = minter.mint(
            scope = scope,
            order = "order-1",
            plan = "atlas",
            task = "engine",
        )

        val expectedSigBase64 = Base64Url.encode(signed.signature)
        assertEquals(expectedSigBase64, signed.signatureBase64Url)

        val expectedWire = "${signed.message}\n$expectedSigBase64"
        assertEquals(expectedWire, signed.wire)
        assertEquals(expectedWire, signed.toWire())

        // toString redacts signature bytes
        val debugString = signed.toString()
        assertTrue(debugString.contains("signature=[REDACTED]"))
        assertFalse(debugString.contains(expectedSigBase64))
    }
}
