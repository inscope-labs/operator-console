package com.inscopelabs.buildengine.data.local

import com.inscopelabs.buildengine.capability.model.OperationType
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PR3-010 `[REG+]` — the journal field cipher must bind every ciphertext to its record and its
 * column through GCM associated data, so a stored ciphertext cannot be replayed into another
 * record or another column.
 *
 * These tests exercise the production code path ([JournalGcm] and [OperationJournalCodec]) with a
 * JVM-generated AES key: the Keystore-backed cipher delegates to exactly these functions, so
 * proving the behaviour here proves the production behaviour rather than a parallel implementation.
 */
class JournalFieldCipherAssociatedDataTest {

    // -- Associated data derivation ------------------------------------------------------

    @Test
    fun `associated data is deterministic for the same record and column`() {
        val first = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH)
        val second = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH)

        assertArrayEquals(first, second)
    }

    @Test
    fun `associated data differs between two columns of the same record`() {
        val perColumn = JournalColumn.entries.associateWith { column ->
            JournalAssociatedData.forColumn(RECORD_KEY, column)
        }

        val distinct = perColumn.values.map { it.toList() }.distinct()
        assertEquals(
            "every encrypted column must derive its own associated data",
            JournalColumn.entries.size,
            distinct.size,
        )
    }

    @Test
    fun `associated data differs between two records of the same column`() {
        val first = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.TASK)
        val second = JournalAssociatedData.forColumn(OTHER_RECORD_KEY, JournalColumn.TASK)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `every record and column pair derives distinct associated data`() {
        val derived = mutableListOf<ByteArray>()
        for (recordKey in listOf(RECORD_KEY, OTHER_RECORD_KEY, "a", "ab")) {
            for (column in JournalColumn.entries) {
                derived += JournalAssociatedData.forColumn(recordKey, column)
            }
        }

        val distinct = derived.map { it.toList() }.distinct()
        assertEquals("record and column pairs must not collide", derived.size, distinct.size)
    }

    @Test
    fun `associated data encoding is not a naive concatenation of record and column`() {
        // "ab" + PLAN and "a" + PLAN must not collide even though the record key is a prefix of
        // the other: the encoding is length prefixed.
        val splitOne = JournalAssociatedData.forColumn("a", JournalColumn.PLAN)
        val splitTwo = JournalAssociatedData.forColumn("ab", JournalColumn.PLAN)

        assertFalse(splitOne.contentEquals(splitTwo))
        assertNotEquals(
            "the record key must not be recoverable by slicing a naive concatenation",
            String(splitOne, Charsets.UTF_8),
            String(splitTwo, Charsets.UTF_8),
        )
    }

    @Test
    fun `associated data rejects a blank record key`() {
        assertThrows(IllegalArgumentException::class.java) {
            JournalAssociatedData.forColumn("   ", JournalColumn.OPERATION)
        }
    }

    // -- Production cipher mechanics -----------------------------------------------------

    @Test
    fun `journal gcm round trips a field with matching associated data`() {
        val key = testKey()
        val aad = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH)

        val ciphertext = JournalGcm.encryptToWire(key, PLAINTEXT, aad)

        assertTrue(ciphertext.startsWith(JournalGcm.FORMAT_PREFIX))
        assertFalse("ciphertext must not contain the plaintext", ciphertext.contains(PLAINTEXT))
        assertEquals(PLAINTEXT, JournalGcm.decryptFromWire(key, ciphertext, aad))
    }

    @Test
    fun `journal gcm produces a distinct ciphertext and iv for every encryption`() {
        val key = testKey()
        val aad = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH)

        val first = JournalGcm.encryptToWire(key, PLAINTEXT, aad)
        val second = JournalGcm.encryptToWire(key, PLAINTEXT, aad)

        assertNotEquals("a fresh IV must be used for every encryption", first, second)
    }

    @Test
    fun `journal gcm fails closed when the associated data does not match`() {
        val key = testKey()
        val aad = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH)
        val ciphertext = JournalGcm.encryptToWire(key, PLAINTEXT, aad)

        val otherColumn = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.TASK)
        val failure = assertThrows(JournalCryptoException::class.java) {
            JournalGcm.decryptFromWire(key, ciphertext, otherColumn)
        }
        assertFalse("the failure must not leak plaintext", failure.message.orEmpty().contains(PLAINTEXT))

        val otherRecord = JournalAssociatedData.forColumn(OTHER_RECORD_KEY, JournalColumn.BRANCH)
        assertThrows(JournalCryptoException::class.java) {
            JournalGcm.decryptFromWire(key, ciphertext, otherRecord)
        }
    }

    @Test
    fun `journal gcm fails closed on a tampered ciphertext`() {
        val key = testKey()
        val aad = JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH)
        val ciphertext = JournalGcm.encryptToWire(key, PLAINTEXT, aad)

        val payload = Base64.getDecoder().decode(ciphertext.removePrefix(JournalGcm.FORMAT_PREFIX))
        payload[payload.size - 1] = (payload[payload.size - 1].toInt() xor 0x01).toByte()
        val tampered = JournalGcm.FORMAT_PREFIX + Base64.getEncoder().encodeToString(payload)

        val failure = assertThrows(JournalCryptoException::class.java) {
            JournalGcm.decryptFromWire(key, tampered, aad)
        }
        assertFalse("the failure must not leak plaintext", failure.message.orEmpty().contains(PLAINTEXT))
    }

    @Test
    fun `journal gcm rejects the legacy format version that carried no associated data`() {
        val key = testKey()
        val legacy = "v1:" + Base64.getEncoder().encodeToString(ByteArray(32))

        val failure = assertThrows(JournalCryptoException::class.java) {
            JournalGcm.decryptFromWire(key, legacy, JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH))
        }
        assertTrue(
            "an unsupported format version must be diagnosable",
            failure.message.orEmpty().contains("Unsupported journal field format version"),
        )
    }

    @Test
    fun `journal gcm rejects a truncated payload`() {
        val key = testKey()
        val truncated = JournalGcm.FORMAT_PREFIX + Base64.getEncoder().encodeToString(ByteArray(8))

        val failure = assertThrows(JournalCryptoException::class.java) {
            JournalGcm.decryptFromWire(key, truncated, JournalAssociatedData.forColumn(RECORD_KEY, JournalColumn.BRANCH))
        }
        assertTrue(
            "a truncated payload must be diagnosable",
            failure.message.orEmpty().contains("truncated"),
        )
    }

    // -- Journal codec: the columns are bound in the record that owns them ----------------

    @Test
    fun `codec encrypts every column with its own associated data`() {
        val recorder = RecordingCipher()
        val intent = sampleIntent()

        val entity = OperationJournalCodec.encryptNewEntity(RECORD_KEY, intent, recorder, NOW)

        assertEquals(6, recorder.associatedData.size)
        assertEquals(
            "each encrypted column must be bound to its own associated data",
            6,
            recorder.associatedData.map { it.toList() }.distinct().size,
        )
        for (aad in recorder.associatedData) {
            assertTrue(
                "the associated data must carry the record key",
                String(aad, Charsets.UTF_8).contains(RECORD_KEY),
            )
        }
        assertEquals(OperationJournalState.INTENT_RECORDED, entity.state)
    }

    @Test
    fun `codec round trips every encrypted column of a record`() {
        val cipher = AadBindingCipher(testKey())
        val intent = sampleIntent()

        val entity = OperationJournalCodec.encryptNewEntity(RECORD_KEY, intent, cipher, NOW)
        val record = OperationJournalCodec.decryptRecord(entity, cipher)

        assertEquals(RECORD_KEY, record.idempotencyKey)
        assertEquals(OperationType.PUBLISH, record.intent.operation)
        assertEquals(intent.plan, record.intent.plan)
        assertEquals(intent.task, record.intent.task)
        assertEquals(intent.branch, record.intent.branch)
        assertEquals(intent.expectedHead, record.intent.expectedHead)
    }

    @Test
    fun `codec fails closed when a ciphertext is moved to another column`() {
        val cipher = AadBindingCipher(testKey())
        val intent = sampleIntent()
        val entity = OperationJournalCodec.encryptNewEntity(RECORD_KEY, intent, cipher, NOW)

        val swapped = entity.copy(
            encryptedPlan = entity.encryptedTask,
            encryptedTask = entity.encryptedPlan,
        )

        val failure = assertThrows(JournalCryptoException::class.java) {
            OperationJournalCodec.decryptRecord(swapped, cipher)
        }
        assertFalse("the failure must not leak plaintext", failure.message.orEmpty().contains(intent.plan))
    }

    @Test
    fun `codec fails closed when a ciphertext is replayed into another record`() {
        val cipher = AadBindingCipher(testKey())
        val intent = sampleIntent()
        val first = OperationJournalCodec.encryptNewEntity(RECORD_KEY, intent, cipher, NOW)
        val second = OperationJournalCodec.encryptNewEntity(OTHER_RECORD_KEY, intent, cipher, NOW)

        val replayed = second.copy(encryptedTask = first.encryptedTask)

        assertThrows(JournalCryptoException::class.java) {
            OperationJournalCodec.decryptRecord(replayed, cipher)
        }
    }

    @Test
    fun `codec fails closed when an encrypted column is tampered with`() {
        val cipher = AadBindingCipher(testKey())
        val intent = sampleIntent()
        val entity = OperationJournalCodec.encryptNewEntity(RECORD_KEY, intent, cipher, NOW)

        val tampered = entity.copy(encryptedOperation = flipLastByte(entity.encryptedOperation))

        val failure = assertThrows(JournalCryptoException::class.java) {
            OperationJournalCodec.decryptRecord(tampered, cipher)
        }
        assertFalse("the failure must not leak plaintext", failure.message.orEmpty().contains(OperationType.PUBLISH.name))
    }

    /** Corrupts the GCM tag of a wire-format ciphertext. */
    private fun flipLastByte(ciphertext: String): String {
        val payload = Base64.getDecoder().decode(ciphertext.removePrefix(JournalGcm.FORMAT_PREFIX))
        payload[payload.size - 1] = (payload[payload.size - 1].toInt() xor 0x01).toByte()
        return JournalGcm.FORMAT_PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    // -- Helpers --------------------------------------------------------------------------

    private fun sampleIntent(): OperationIntent = OperationIntent(
        operation = OperationType.PUBLISH,
        plan = "plan-10008",
        task = "task-10008-refactor-parser",
        branch = "feature/refactor-parser",
        expectedHead = HEAD_SHA,
    )

    private fun testKey(): SecretKey {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SecretKeySpec(bytes, "AES")
    }

    /**
     * Real production cipher path with a JVM key: exercises [JournalGcm] and the real wire
     * format, including the associated-data binding.
     */
    private class AadBindingCipher(private val key: SecretKey) : JournalFieldCipher {
        override fun encrypt(plaintext: String, associatedData: ByteArray): String =
            JournalGcm.encryptToWire(key, plaintext, associatedData)

        override fun decrypt(ciphertext: String, associatedData: ByteArray): String =
            JournalGcm.decryptFromWire(key, ciphertext, associatedData)
    }

    /** Records the associated data used for each encryption without transforming the plaintext. */
    @Suppress("UNUSED_PARAMETER")
    private class RecordingCipher : JournalFieldCipher {
        val associatedData = mutableListOf<ByteArray>()

        override fun encrypt(plaintext: String, associatedData: ByteArray): String {
            this.associatedData += associatedData
            return "recorded:$plaintext"
        }

        override fun decrypt(ciphertext: String, associatedData: ByteArray): String =
            ciphertext.removePrefix("recorded:")
    }

    private companion object {
        const val RECORD_KEY = "0123456789abcdef0123456789abcdef"
        const val OTHER_RECORD_KEY = "fedcba9876543210fedcba9876543210"
        const val PLAINTEXT = "feature/refactor-parser"
        const val HEAD_SHA = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
        const val NOW = 1_762_000_000_000L
    }
}
