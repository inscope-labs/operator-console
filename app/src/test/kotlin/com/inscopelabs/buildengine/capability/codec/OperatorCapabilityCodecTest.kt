package com.inscopelabs.buildengine.capability.codec

import com.inscopelabs.buildengine.capability.model.OperatorCapability
import com.inscopelabs.buildengine.capability.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the ctxop-v1 framing: LF separation, field order, base64url without
 * padding, and the field validation rules.
 *
 * The codec is a contract with the gateway, so the assertions here are the
 * contract: what the framing must be, and what must be refused. A codec that
 * repairs an invalid value is a codec that lets two different capabilities have the
 * same signed bytes.
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

    // -- framing ----------------------------------------------------------------

    @Test
    fun `the message is ten LF-separated fields`() {
        val fields = OperatorCapabilityCodec.message(sample).split("\n")

        assertEquals(OperatorCapabilityCodec.FIELD_COUNT, fields.size)
    }

    @Test
    fun `the field order is the fixed one`() {
        val fields = OperatorCapabilityCodec.message(sample).split("\n")

        assertEquals("ctxop-v1", fields[0])
        assertEquals("k1", fields[1])
        assertEquals("1760000000", fields[2])
        assertEquals("order-1", fields[3])
        assertEquals("plan-1", fields[4])
        assertEquals("task-1", fields[5])
        assertEquals("PUBLISH", fields[6])
        assertEquals("task-10008-abc", fields[7])
        assertEquals(sample.head, fields[8])
        assertEquals("nonce-1", fields[9])
    }

    @Test
    fun `the message carries no trailing separator and no CR`() {
        val message = OperatorCapabilityCodec.message(sample)

        assertFalse(message.endsWith("\n"))
        assertFalse(message.contains("\r"))
        assertEquals(9, message.count { it == '\n' })
    }

    @Test
    fun `the signed bytes are the UTF-8 bytes of the message`() {
        val message = OperatorCapabilityCodec.message(sample)

        assertArrayEqualsBytes(
            message.toByteArray(Charsets.UTF_8),
            OperatorCapabilityCodec.signedBytes(sample),
        )
    }

    @Test
    fun `the signature rides on a line of its own`() {
        val signature = ByteArray(32) { it.toByte() }

        val transmission = OperatorCapabilityCodec.encode(sample, signature)

        val lines = transmission.split("\n")
        assertEquals(OperatorCapabilityCodec.FIELD_COUNT + 1, lines.size)
        assertEquals(OperatorCapabilityCodec.message(sample), lines.subList(0, 10).joinToString("\n"))
        // base64url, without padding.
        assertFalse(lines[10].contains("="))
        assertFalse(lines[10].contains("+"))
        assertFalse(lines[10].contains("/"))
    }

    @Test
    fun `a transmission splits back into the message and the signature`() {
        val signature = ByteArray(32) { (it * 3 + 1).toByte() }

        val transmission = OperatorCapabilityCodec.encode(sample, signature)
        val (message, decodedSignature) = OperatorCapabilityCodec.split(transmission)

        assertEquals(OperatorCapabilityCodec.message(sample), message)
        assertArrayEqualsBytes(signature, decodedSignature)
    }

    // -- round trip -------------------------------------------------------------

    @Test
    fun `a capability round-trips through the message`() {
        val decoded = OperatorCapabilityCodec.decode(OperatorCapabilityCodec.message(sample))

        assertEquals(sample, decoded)
    }

    @Test
    fun `every operation round-trips`() {
        for (op in OperationType.entries) {
            val capability = sample.copy(op = op)

            val decoded = OperatorCapabilityCodec.decode(OperatorCapabilityCodec.message(capability))

            assertEquals(capability, decoded)
        }
    }

    @Test
    fun `the operation wire names are the documented enumeration`() {
        // docs/capability-format.md §3 lists the closed set.
        val documented = listOf(
            "PUBLISH", "UPLOAD", "RECONCILE", "DECIDE", "HALT", "RESUME",
            "SYNC", "DOCTOR", "ORDER", "SET_IN_PROGRESS", "SET_DONE", "NEW_PLAN",
        )

        assertEquals(documented, OperationType.entries.map { it.name })
    }

    @Test
    fun `the worked example from the capability format decodes`() {
        // docs/capability-format.md §4 — the specification's own example must
        // satisfy the specification's own field rules.
        val message = """
            ctxop-v1
            k2
            1761840000
            order-7731
            atlas
            10008
            PUBLISH
            task-10008-refactor-parser
            9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d
            7f3c9b1e5d2a7046
        """.trimIndent()

        val capability = OperatorCapabilityCodec.decode(message)

        assertEquals("k2", capability.kid)
        assertEquals(1_761_840_000L, capability.exp)
        assertEquals("order-7731", capability.order)
        assertEquals("atlas", capability.plan)
        assertEquals("10008", capability.task)
        assertEquals(OperationType.PUBLISH, capability.op)
        assertEquals("task-10008-refactor-parser", capability.branch)
        assertEquals("9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d", capability.head)
        assertEquals("7f3c9b1e5d2a7046", capability.nonce)
    }

    // -- refusals ---------------------------------------------------------------

    @Test
    fun `a message with an extra field is refused`() {
        val message = OperatorCapabilityCodec.message(sample) + "\nextra"

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(message)
        }
    }

    @Test
    fun `a message with a missing field is refused`() {
        val message = OperatorCapabilityCodec.message(sample).substringBeforeLast("\n")

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(message)
        }
    }

    @Test
    fun `a trailing separator is refused`() {
        val message = OperatorCapabilityCodec.message(sample) + "\n"

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(message)
        }
    }

    @Test
    fun `a foreign domain separator is refused`() {
        val message = messageWith(FIELD_SEPARATOR_INDEX, "ctxwrite-v1")

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(message)
        }
    }

    @Test
    fun `a CR is refused`() {
        val message = messageWith(FIELD_PLAN_INDEX, "plan-1\r")

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(message)
        }
    }

    @Test
    fun `an invalid kid is refused`() {
        for (kid in listOf("", "k".repeat(17), "k-1", "k 1")) {
            val message = messageWith(FIELD_KID_INDEX, kid)

            assertThrows("kid '$kid' was accepted", IllegalArgumentException::class.java) {
                OperatorCapabilityCodec.decode(message)
            }
        }
    }

    @Test
    fun `an invalid expiry is refused`() {
        for (exp in listOf("not-a-number", "", "-1", "1.5")) {
            val message = messageWith(FIELD_EXP_INDEX, exp)

            assertThrows("exp '$exp' was accepted", IllegalArgumentException::class.java) {
                OperatorCapabilityCodec.decode(message)
            }
        }
    }

    @Test
    fun `an empty identifier is refused`() {
        for (index in listOf(FIELD_ORDER_INDEX, FIELD_PLAN_INDEX, FIELD_TASK_INDEX, FIELD_NONCE_INDEX)) {
            val message = messageWith(index, "")

            assertThrows("field $index was accepted empty", IllegalArgumentException::class.java) {
                OperatorCapabilityCodec.decode(message)
            }
        }
    }

    @Test
    fun `an unknown operation is refused`() {
        val message = messageWith(FIELD_OP_INDEX, "publish")

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(message)
        }
    }

    @Test
    fun `a branch that is not a task branch is refused`() {
        for (branch in listOf("main", "task-", "task-10008-abc/x", "TASK-10008")) {
            val message = messageWith(FIELD_BRANCH_INDEX, branch)

            assertThrows("branch '$branch' was accepted", IllegalArgumentException::class.java) {
                OperatorCapabilityCodec.decode(message)
            }
        }
    }

    @Test
    fun `a head that is not a full git SHA is refused`() {
        for (head in listOf("", "0".repeat(39), "0".repeat(41), "0".repeat(39) + "g", "0".repeat(39) + "\n")) {
            val message = messageWith(FIELD_HEAD_INDEX, head)

            assertThrows("head '$head' was accepted", IllegalArgumentException::class.java) {
                OperatorCapabilityCodec.decode(message)
            }
        }
    }

    @Test
    fun `an invalid capability is not encoded`() {
        val invalid = sample.copy(branch = "main")

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.message(invalid)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.encode(invalid, ByteArray(32))
        }
    }

    @Test
    fun `a transmission without a signature is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.encode(sample, ByteArray(0))
        }
    }

    @Test
    fun `a transmission with the wrong number of fields is refused`() {
        val transmission = OperatorCapabilityCodec.encode(sample, ByteArray(32))

        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.split(transmission + "\nextra")
        }
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.split(OperatorCapabilityCodec.message(sample))
        }
    }

    // -- field rules ------------------------------------------------------------

    @Test
    fun `the branch rule accepts task branches only`() {
        assertTrue(OperatorCapabilityCodec.isValidBranch("task-10008-abc"))
        assertTrue(OperatorCapabilityCodec.isValidBranch("task-10008.refactor_parser"))
        assertFalse(OperatorCapabilityCodec.isValidBranch("main"))
        assertFalse(OperatorCapabilityCodec.isValidBranch("task-"))
        assertFalse(OperatorCapabilityCodec.isValidBranch(""))
    }

    @Test
    fun `the head rule accepts a full lowercase git SHA only`() {
        assertTrue(OperatorCapabilityCodec.isValidHead("0".repeat(40)))
        assertFalse(OperatorCapabilityCodec.isValidHead("0".repeat(39)))
        assertFalse(OperatorCapabilityCodec.isValidHead("A".repeat(40)))
        assertFalse(OperatorCapabilityCodec.isValidHead(""))
    }

    @Test
    fun `the kid rule accepts a selector only`() {
        assertTrue(OperatorCapabilityCodec.isValidKid("k1"))
        assertTrue(OperatorCapabilityCodec.isValidKid("K_2"))
        assertFalse(OperatorCapabilityCodec.isValidKid(""))
        assertFalse(OperatorCapabilityCodec.isValidKid("k".repeat(17)))
        assertFalse(OperatorCapabilityCodec.isValidKid("k-1"))
    }

    @Test
    fun `the operation rule refuses anything outside the enumeration`() {
        assertTrue(OperatorCapabilityCodec.isValidOperation("PUBLISH"))
        assertTrue(OperatorCapabilityCodec.isValidOperation("SET_IN_PROGRESS"))
        assertFalse(OperatorCapabilityCodec.isValidOperation("publish"))
        assertFalse(OperatorCapabilityCodec.isValidOperation("BOOTSTRAP"))
        assertFalse(OperatorCapabilityCodec.isValidOperation(""))
    }

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) {
        assertTrue(
            "expected ${expected.toList()} but was ${actual.toList()}",
            expected.contentEquals(actual),
        )
    }

    /**
     * The sample's message with one field replaced.
     *
     * The message is built from the valid sample and then edited, rather than
     * built from an invalid capability — otherwise the *encoder's* validation would
     * reject it first and the decoder would never be reached. These tests are about
     * what the decoder refuses.
     */
    private fun messageWith(fieldIndex: Int, value: String): String {
        val fields = OperatorCapabilityCodec.message(sample).split("\n").toMutableList()
        fields[fieldIndex] = value
        return fields.joinToString("\n")
    }

    private companion object {
        const val FIELD_SEPARATOR_INDEX = 0
        const val FIELD_KID_INDEX = 1
        const val FIELD_EXP_INDEX = 2
        const val FIELD_ORDER_INDEX = 3
        const val FIELD_PLAN_INDEX = 4
        const val FIELD_TASK_INDEX = 5
        const val FIELD_OP_INDEX = 6
        const val FIELD_BRANCH_INDEX = 7
        const val FIELD_HEAD_INDEX = 8
        const val FIELD_NONCE_INDEX = 9
    }
}
