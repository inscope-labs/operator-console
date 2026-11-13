package com.inscopelabs.buildengine.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.util.Redactor
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lifecycle states of a journaled operation (T3-202).
 *
 * Explicitly separates pre-send intent recording, in-flight dispatch, synchronous acknowledgement,
 * terminal outcomes, uncertain state awaiting lookup, and manual recovery required.
 */
enum class OperationJournalState {
    /** Intent and idempotency key written durably BEFORE any network send. */
    INTENT_RECORDED,

    /** Write request dispatched; waiting for response. */
    SENT,

    /** Synchronous gateway acknowledgement received; outcome validation in progress. */
    ACKNOWLEDGED,

    /** Verified terminal success. */
    TERMINAL_SUCCEEDED,

    /** Gateway refused with a non-conflict contract code. */
    TERMINAL_REFUSED,

    /** Gateway refused with a concurrency/replay/immutability conflict contract code. */
    TERMINAL_CONFLICT,

    /** Outcome uncertain (send timeout, post-send crash, `pending`, or live head drift). */
    UNCERTAIN,

    /** Failed, `404 OPERATION_NOT_FOUND`, unknown status/code, or journal crypto error. */
    RECOVERY_REQUIRED,
}

/**
 * Non-secret request parameters that uniquely identify an operator write intent (T3-202, T3-204).
 *
 * Never holds capability strings, signatures, capability nonces, bearer tokens, or raw `zipBase64`
 * payloads.
 */
data class OperationIntent(
    val operation: OperationType,
    val plan: String = "",
    val task: String = "",
    val branch: String,
    val expectedHead: String,
    val submissionId: String = "",
    val zipSha256: String = "",
    val reason: String = "",
    val targetStatus: String = "",
    val decision: String = "",
) {
    init {
        val checkedFields = listOf(
            plan,
            task,
            branch,
            expectedHead,
            submissionId,
            zipSha256,
            reason,
            targetStatus,
            decision,
        )
        require(checkedFields.none { Redactor.containsForbidden(it) }) {
            "OperationIntent must never contain capability strings, signatures, nonces, or secrets"
        }
    }

    /**
     * Canonical deterministic SHA-256 fingerprint over every non-secret intent field.
     */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fields = listOf(
            operation.name,
            plan,
            task,
            branch,
            expectedHead,
            submissionId,
            zipSha256,
            reason,
            targetStatus,
            decision,
        )
        for (field in fields) {
            val utf8 = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(utf8.size).array())
            digest.update(utf8)
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    fun toExtra(): OperationIntentExtra = OperationIntentExtra(
        submissionId = submissionId,
        zipSha256 = zipSha256,
        reason = reason,
        targetStatus = targetStatus,
        decision = decision,
    )
}

@Serializable
data class OperationIntentExtra(
    val submissionId: String = "",
    val zipSha256: String = "",
    val reason: String = "",
    val targetStatus: String = "",
    val decision: String = "",
)

/**
 * Thrown when journal key provisioning, encryption, or decryption fails.
 * Always handled fail-closed -> [OperationJournalState.RECOVERY_REQUIRED].
 */
class JournalCryptoException(
    message: String,
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)

/**
 * Field-level encryption abstraction for the Room operation journal (T3-202).
 *
 * Kept behind an interface so JVM tests can substitute a fake or simulated failing cipher.
 */
interface JournalFieldCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

/**
 * AndroidKeyStore-backed AES-256-GCM field cipher using platform `javax.crypto` (T3-202).
 *
 * - Non-exportable AES key stored in `AndroidKeyStore`.
 * - Fresh random 12-byte IV generated per encrypted field (`setRandomizedEncryptionRequired(true)`).
 * - Fails closed by throwing [JournalCryptoException] on any key, IV, tag, or format error.
 */
class AndroidKeystoreJournalCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val keyStoreProvider: () -> KeyStore = {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    },
) : JournalFieldCipher {

    override fun encrypt(plaintext: String): String {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            if (iv == null || iv.size != GCM_IV_LENGTH_BYTES) {
                throw JournalCryptoException("Keystore cipher produced invalid IV length")
            }
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            FORMAT_PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (e: JournalCryptoException) {
            throw e
        } catch (e: Exception) {
            throw JournalCryptoException("Failed to encrypt journal field", e)
        }
    }

    override fun decrypt(ciphertext: String): String {
        return try {
            if (!ciphertext.startsWith(FORMAT_PREFIX)) {
                throw JournalCryptoException("Malformed encrypted journal field prefix")
            }
            val payload = Base64.getDecoder().decode(ciphertext.removePrefix(FORMAT_PREFIX))
            if (payload.size < GCM_IV_LENGTH_BYTES + GCM_MIN_TAG_BYTES) {
                throw JournalCryptoException("Encrypted journal field payload is truncated")
            }
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherBytes = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
            val secretKey = getExistingKey()
                ?: throw JournalCryptoException("Journal encryption key '$keyAlias' is missing")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decrypted = cipher.doFinal(cipherBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: JournalCryptoException) {
            throw e
        } catch (e: Exception) {
            throw JournalCryptoException("Failed to decrypt journal field", e)
        }
    }

    private fun getExistingKey(): SecretKey? {
        val store = keyStoreProvider()
        if (!store.containsAlias(keyAlias)) return null
        val entry = store.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw JournalCryptoException("Keystore entry '$keyAlias' is not a SecretKey")
        return entry.secretKey
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS: String = "operation_journal_aes_key"
        private const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS: Int = 256
        private const val GCM_IV_LENGTH_BYTES: Int = 12
        private const val GCM_TAG_BITS: Int = 128
        private const val GCM_MIN_TAG_BYTES: Int = GCM_TAG_BITS / 8
        private const val FORMAT_PREFIX: String = "v1:"
    }
}

/**
 * Room converters for [OperationJournalEntity].
 */
class OperationJournalConverters {

    @TypeConverter
    fun fromState(state: OperationJournalState): String = state.name

    @TypeConverter
    fun toState(value: String): OperationJournalState =
        OperationJournalState.entries.firstOrNull { it.name == value }
            ?: OperationJournalState.RECOVERY_REQUIRED
}

/**
 * Encrypted Room journal record for a privileged write operation (ADR 0009, T3-202).
 *
 * Every intent detail, audit reference, result head SHA, and error code is encrypted with
 * [JournalFieldCipher]. Capability strings, signatures, nonces, and raw upload bytes are NEVER
 * present in any field.
 */
@Entity(
    tableName = "operation_journal",
    indices = [
        Index(value = ["intentFingerprint"]),
    ],
)
data class OperationJournalEntity(
    @PrimaryKey
    val idempotencyKey: String,
    val intentFingerprint: String,
    val encryptedOperation: String,
    val encryptedPlan: String,
    val encryptedTask: String,
    val encryptedBranch: String,
    val encryptedExpectedHead: String,
    val encryptedExtraFields: String,
    val state: OperationJournalState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val encryptedAuditRef: String? = null,
    val encryptedResultHeadSha: String? = null,
    val encryptedLastErrorCode: String? = null,
)

/**
 * Decrypted view of an [OperationJournalEntity] for in-memory repository/recovery logic.
 */
data class DecryptedJournalRecord(
    val idempotencyKey: String,
    val intent: OperationIntent,
    val state: OperationJournalState,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val auditRef: String? = null,
    val resultHeadSha: String? = null,
    val lastErrorCode: String? = null,
)

internal object OperationJournalCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encryptNewEntity(
        idempotencyKey: String,
        intent: OperationIntent,
        cipher: JournalFieldCipher,
        nowEpochMs: Long,
    ): OperationJournalEntity {
        val extraJson = json.encodeToString(intent.toExtra())
        return OperationJournalEntity(
            idempotencyKey = idempotencyKey,
            intentFingerprint = intent.fingerprint(),
            encryptedOperation = cipher.encrypt(intent.operation.name),
            encryptedPlan = cipher.encrypt(intent.plan),
            encryptedTask = cipher.encrypt(intent.task),
            encryptedBranch = cipher.encrypt(intent.branch),
            encryptedExpectedHead = cipher.encrypt(intent.expectedHead),
            encryptedExtraFields = cipher.encrypt(extraJson),
            state = OperationJournalState.INTENT_RECORDED,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
    }

    fun decryptRecord(
        entity: OperationJournalEntity,
        cipher: JournalFieldCipher,
    ): DecryptedJournalRecord {
        val opWire = cipher.decrypt(entity.encryptedOperation)
        val op = OperationType.entries.firstOrNull { it.name == opWire }
            ?: throw JournalCryptoException("Unknown decrypted operation type")
        val plan = cipher.decrypt(entity.encryptedPlan)
        val task = cipher.decrypt(entity.encryptedTask)
        val branch = cipher.decrypt(entity.encryptedBranch)
        val expectedHead = cipher.decrypt(entity.encryptedExpectedHead)
        val extraJson = cipher.decrypt(entity.encryptedExtraFields)
        val extra = try {
            json.decodeFromString<OperationIntentExtra>(extraJson)
        } catch (e: Exception) {
            throw JournalCryptoException("Failed to decode decrypted extra fields", e)
        }
        val auditRef = entity.encryptedAuditRef?.let { cipher.decrypt(it) }
        val resultHeadSha = entity.encryptedResultHeadSha?.let { cipher.decrypt(it) }
        val lastErrorCode = entity.encryptedLastErrorCode?.let { cipher.decrypt(it) }

        return DecryptedJournalRecord(
            idempotencyKey = entity.idempotencyKey,
            intent = OperationIntent(
                operation = op,
                plan = plan,
                task = task,
                branch = branch,
                expectedHead = expectedHead,
                submissionId = extra.submissionId,
                zipSha256 = extra.zipSha256,
                reason = extra.reason,
                targetStatus = extra.targetStatus,
                decision = extra.decision,
            ),
            state = entity.state,
            createdAtEpochMs = entity.createdAtEpochMs,
            updatedAtEpochMs = entity.updatedAtEpochMs,
            auditRef = auditRef,
            resultHeadSha = resultHeadSha,
            lastErrorCode = lastErrorCode,
        )
    }
}
