package com.inscopelabs.buildengine.capability.codec

import com.inscopelabs.buildengine.capability.model.OperatorCapability
import com.inscopelabs.buildengine.capability.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Covers the ctxop-v1 framing: LF separation, field order, base64url without
 * padding, and the field validation rules.
 *
 * SCAFFOLD PLACEHOLDER — the assertions below pin the API shape only. Replace them
 * with real round-trip and rejection tests.
 */
class OperatorCapabilityCodecTest {

    private val sample = OperatorCapability(
        kid = "k1",
        exp = 1_760_000_000L,
        order = "order-1",
        plan = "plan-1",
        task = "task-1",
        op = OperationType.PUBLISH,
        branch = "task-10008-abc",
        head = "0".repeat(40),
        nonce = "nonce-1",
    )

    @Test
    fun `codec exposes the domain separator and the field patterns`() {
        assertEquals("ctxop-v1", OperatorCapabilityCodec.DOMAIN_SEPARATOR)
        assertEquals("^task-[A-Za-z0-9._-]+$", OperatorCapabilityCodec.BRANCH_PATTERN)
        assertEquals("^[0-9a-f]{40}$", OperatorCapabilityCodec.HEAD_PATTERN)
    }

    @Test
    fun `capability carries the scope fields`() {
        assertEquals(OperationType.PUBLISH, sample.op)
        assertEquals("task-10008-abc", sample.branch)
        assertEquals(40, sample.head.length)
        assertNotNull(sample.nonce)
    }
}
