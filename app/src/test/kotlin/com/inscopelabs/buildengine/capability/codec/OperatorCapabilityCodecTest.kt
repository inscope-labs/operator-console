package com.inscopelabs.buildengine.capability.codec

import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.model.OperatorCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the ctxop-v1 framing: LF separation, field order, base64url without
 * padding, and the field validation rules.
 */
class OperatorCapabilityCodecTest {

    private val validTaskCapability = OperatorCapability(
        kid = "k2",
        exp = 1761840000L,
        order = "order-7731",
        plan = "atlas",
        task = "10008",
        op = OperationType.PUBLISH,
        branch = "task-10008-refactor-parser",
        head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
        nonce = "7f3c9b1e5d2a7046",
    )

    private val validEngineCapability = OperatorCapability(
        kid = "k2",
        exp = 1761840000L,
        order = "order-7731",
        plan = "atlas",
        task = "engine",
        op = OperationType.HALT,
        branch = "engine",
        head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
        nonce = "7f3c9b1e5d2a7046",
    )

    @Test
    fun `codec exposes the domain separator and the field patterns`() {
        assertEquals("ctxop-v1", OperatorCapabilityCodec.DOMAIN_SEPARATOR)
        assertEquals("^task-[A-Za-z0-9._-]+$", OperatorCapabilityCodec.BRANCH_PATTERN)
        assertEquals("^[0-9a-f]{40}$", OperatorCapabilityCodec.HEAD_PATTERN)
        assertEquals("^[A-Za-z0-9_]{1,16}$", OperatorCapabilityCodec.KID_PATTERN)
    }

    @Test
    fun `encode produces exactly 10 LF-separated lines matching canonical format`() {
        val encoded = OperatorCapabilityCodec.encode(validTaskCapability)
        val lines = encoded.split("\n")
        assertEquals(10, lines.size)
        assertFalse(encoded.contains("\r"))
        assertFalse(encoded.endsWith("\n"))
        assertEquals("ctxop-v1", lines[0])
        assertEquals("k2", lines[1])
        assertEquals("1761840000", lines[2])
        assertEquals("order-7731", lines[3])
        assertEquals("atlas", lines[4])
        assertEquals("10008", lines[5])
        assertEquals("PUBLISH", lines[6])
        assertEquals("task-10008-refactor-parser", lines[7])
        assertEquals("9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d", lines[8])
        assertEquals("7f3c9b1e5d2a7046", lines[9])
    }

    @Test
    fun `roundtrip encode and decode succeeds for task-scoped capability`() {
        val encoded = OperatorCapabilityCodec.encode(validTaskCapability)
        val decoded = OperatorCapabilityCodec.decode(encoded)
        assertEquals(validTaskCapability, decoded)
    }

    @Test
    fun `roundtrip encode and decode succeeds for engine-scoped capability`() {
        val encoded = OperatorCapabilityCodec.encode(validEngineCapability)
        val decoded = OperatorCapabilityCodec.decode(encoded)
        assertEquals(validEngineCapability, decoded)
    }

    @Test
    fun `encode with signature appends signature line using base64url no padding`() {
        val dummySig = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val wire = OperatorCapabilityCodec.encode(validTaskCapability, dummySig)
        val lines = wire.split("\n")
        assertEquals(11, lines.size)
        assertEquals("AQIDBA", lines[10])
    }

    @Test
    fun `strict decode rejects missing fields`() {
        val nineLines = "ctxop-v1\nk2\n1761840000\norder-1\nplan-1\ntask-1\nPUBLISH\ntask-1\n9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(nineLines)
        }
    }

    @Test
    fun `strict decode rejects extra fields`() {
        val elevenLines = OperatorCapabilityCodec.encode(validTaskCapability) + "\nextra_field"
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(elevenLines)
        }
    }

    @Test
    fun `strict decode rejects CRLF`() {
        val crlf = OperatorCapabilityCodec.encode(validTaskCapability).replace("\n", "\r\n")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(crlf)
        }
    }

    @Test
    fun `strict decode rejects trailing LF`() {
        val trailing = OperatorCapabilityCodec.encode(validTaskCapability) + "\n"
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(trailing)
        }
    }

    @Test
    fun `strict decode rejects leading LF`() {
        val leading = "\n" + OperatorCapabilityCodec.encode(validTaskCapability)
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(leading)
        }
    }

    @Test
    fun `strict decode rejects invalid domain separator`() {
        val badDomain = OperatorCapabilityCodec.encode(validTaskCapability).replace("ctxop-v1", "ctxwrite-v1")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(badDomain)
        }
    }

    @Test
    fun `strict decode rejects unknown operation`() {
        val badOp = OperatorCapabilityCodec.encode(validTaskCapability).replace("PUBLISH", "NON_EXISTENT_OP")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(badOp)
        }
    }

    @Test
    fun `strict decode rejects invalid kid`() {
        val badKid = OperatorCapabilityCodec.encode(validTaskCapability).replace("k2", "invalid!kid!with!specials")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(badKid)
        }
    }

    @Test
    fun `strict decode rejects invalid exp`() {
        val badExp = OperatorCapabilityCodec.encode(validTaskCapability).replace("1761840000", "-100")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(badExp)
        }
    }

    @Test
    fun `strict decode rejects task operation without task- prefix in branch`() {
        val badBranch = OperatorCapabilityCodec.encode(validTaskCapability).replace("task-10008-refactor-parser", "main")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(badBranch)
        }
    }

    @Test
    fun `strict decode rejects malformed head SHA`() {
        val shortHead = OperatorCapabilityCodec.encode(validTaskCapability).replace("9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d", "9f2c1b7e4a5d")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(shortHead)
        }
    }

    @Test
    fun `strict decode rejects malformed nonce`() {
        val badNonce = OperatorCapabilityCodec.encode(validTaskCapability).replace("7f3c9b1e5d2a7046", "bad nonce with space")
        assertThrows(IllegalArgumentException::class.java) {
            OperatorCapabilityCodec.decode(badNonce)
        }
    }

    @Test
    fun `helper validation methods enforce contract patterns`() {
        assertTrue(OperatorCapabilityCodec.isValidBranch("task-10008-fix"))
        assertFalse(OperatorCapabilityCodec.isValidBranch("main"))
        assertFalse(OperatorCapabilityCodec.isValidBranch("feature/task-1"))

        assertTrue(OperatorCapabilityCodec.isValidBranch("task-10008-fix", OperationType.PUBLISH))
        assertFalse(OperatorCapabilityCodec.isValidBranch("main", OperationType.PUBLISH))
        assertTrue(OperatorCapabilityCodec.isValidBranch("engine", OperationType.HALT))

        assertTrue(OperatorCapabilityCodec.isValidHead("9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"))
        assertFalse(OperatorCapabilityCodec.isValidHead("9F2C1B7E4A5D6038C1B2A9E7D4F6081C3B5A7E9D")) // uppercase forbidden
        assertFalse(OperatorCapabilityCodec.isValidHead("9f2c1b7e4a5d")) // short

        assertTrue(OperatorCapabilityCodec.isValidKid("k1"))
        assertTrue(OperatorCapabilityCodec.isValidKid("device_key_2026"))
        assertFalse(OperatorCapabilityCodec.isValidKid("k-1")) // hyphen not in [A-Za-z0-9_]
        assertFalse(OperatorCapabilityCodec.isValidKid("toolongkeynamethatexceeds16chars"))
    }

    private fun decodeWithNonce(nonce: String) =
        OperatorCapabilityCodec.decode(OperatorCapabilityCodec.encode(validTaskCapability).replace("7f3c9b1e5d2a7046", nonce))

    @Test
    fun `nonce must be 16 to 64 lowercase hex characters`() {
        assertEquals("a".repeat(64), decodeWithNonce("a".repeat(64)).nonce)
        assertEquals("a".repeat(16), decodeWithNonce("a".repeat(16)).nonce)
        for (bad in listOf("7f3c9b1e5d2a704", "7F3C9B1E5D2A7046", "a".repeat(65), "nonce-with-dash", "7f3c9b1e5d2a704g")) {
            assertThrows(IllegalArgumentException::class.java) { decodeWithNonce(bad) }
        }
    }

    @Test
    fun `validation errors do not echo the nonce`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validTaskCapability.copy(nonce = "BADNONCE-SECRET")
        }
        assertFalse(error.message!!.contains("BADNONCE-SECRET"))
    }

    @Test
    fun `engine scoped operations accept engine branches and refuse task branches and unsafe names`() {
        for (good in listOf("engine", "main", "release/1.0", "build-engine")) {
            assertEquals(good, validEngineCapability.copy(branch = good).branch)
        }
        for (bad in listOf("task-1", "task-10008-refactor-parser", "../x", "a..b", "-x", "/x", "x/", "engine.lock", "engine.", "")) {
            assertThrows(IllegalArgumentException::class.java) { validEngineCapability.copy(branch = bad) }
        }
    }

    @Test
    fun `task scoped operations refuse engine branches`() {
        for (bad in listOf("engine", "main")) {
            assertThrows(IllegalArgumentException::class.java) { validTaskCapability.copy(branch = bad) }
        }
    }

    @Test
    fun `exp must be a canonical positive decimal`() {
        for (bad in listOf("+1761840000", "01761840000", "-5", "0", "99999999999999999999", "1.5", "1e9")) {
            val message = OperatorCapabilityCodec.encode(validTaskCapability).replace("1761840000", bad)
            assertThrows(IllegalArgumentException::class.java) { OperatorCapabilityCodec.decode(message) }
        }
    }

    @Test
    fun `engine scoped capability round trips`() {
        val decoded = OperatorCapabilityCodec.decode(OperatorCapabilityCodec.encode(validEngineCapability))
        assertEquals(validEngineCapability, decoded)
    }
}
