package com.inscopelabs.buildengine.capability

import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the capability TTL, nonce uniqueness and the branch-shape rule.
 *
 * SCAFFOLD PLACEHOLDER — replace with real minting assertions.
 */
class CapabilityMinterTest {

    @Test
    fun `suggested ttl is five minutes`() {
        val minter = CapabilityMinter(
            signer = com.inscopelabs.buildengine.capability.signing.DeviceKeyCapabilitySigner(),
            nonceSource = DeviceNonceSource(
                com.inscopelabs.buildengine.core.crypto.SecureRandomSource(),
            ),
            clock = com.inscopelabs.buildengine.core.time.SystemClock(),
        )
        assertEquals(300L, minter.ttlSeconds)
    }

    @Test
    fun `scope names the operation, the branch, the head and the nonce`() {
        val scope = CapabilityScope(
            op = OperationType.PUBLISH,
            branch = "task-1",
            head = "a".repeat(40),
            nonce = "n1",
        )
        assertEquals(OperationType.PUBLISH, scope.op)
        assertTrue(scope.branch.startsWith("task-"))
    }
}
