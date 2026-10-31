package com.inscopelabs.buildengine.capability.codec

import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.model.OperatorCapability
import com.inscopelabs.buildengine.testutil.VectorLoader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the codec against the shared conformance vectors (docs/fixtures/vectors/), so the
 * Android client and the gateway are held to the same bytes.
 */
class OperatorCapabilityCodecVectorsTest {

    @Test
    fun `every valid vector encodes to its canonical message and decodes back`() {
        val vectors = VectorLoader.vectors("valid_capabilities.json")
        assertTrue("valid vectors must not be empty", vectors.isNotEmpty())
        for (vector in vectors) {
            val id = vector.getValue("id").jsonPrimitive.content
            val capability = capabilityOf(vector.getValue("fields").jsonObject)
            val canonical = vector.getValue("canonicalMessage").jsonPrimitive.content
            assertEquals("encode $id", canonical, OperatorCapabilityCodec.encode(capability))
            assertEquals("decode $id", capability, OperatorCapabilityCodec.decode(canonical))
        }
    }

    @Test
    fun `every mutation vector is rejected by the decoder`() {
        val vectors = VectorLoader.vectors("codec_mutations.json")
        assertTrue("mutation vectors must not be empty", vectors.isNotEmpty())
        for (vector in vectors) {
            val id = vector.getValue("id").jsonPrimitive.content
            val raw = vector.getValue("rawMessage").jsonPrimitive.content
            val rejected = try {
                OperatorCapabilityCodec.decode(raw)
                false
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue("$id must be rejected", rejected)
        }
    }

    private fun capabilityOf(fields: JsonObject): OperatorCapability = OperatorCapability(
        kid = fields.getValue("kid").jsonPrimitive.content,
        exp = fields.getValue("exp").jsonPrimitive.long,
        order = fields.getValue("order").jsonPrimitive.content,
        plan = fields.getValue("plan").jsonPrimitive.content,
        task = fields.getValue("task").jsonPrimitive.content,
        op = OperationType.valueOf(fields.getValue("op").jsonPrimitive.content),
        branch = fields.getValue("branch").jsonPrimitive.content,
        head = fields.getValue("head").jsonPrimitive.content,
        nonce = fields.getValue("nonce").jsonPrimitive.content,
    )
}
