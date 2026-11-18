package com.inscopelabs.buildengine.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.util.Redactor
import java.io.ByteArrayOutputStream
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
    ;

    /**
     * Explicit forward-only state machine for operation journal entries (T3-202, PR3-011).
     *
     * Transitions cannot move backwards (e.g. `SENT -> INTENT_RECORDED`, `ACKNOWLEDGED -> SENT`,
     * `UNCERTAIN -> SENT/ACKNOWLEDGED`), cannot skip `SENT` from `INTENT_RECORDED` to hit
     * acknowledgement/uncertain/terminal outcome states, and cannot leave any terminal state.
     */
    fun allowedNextStates(): Set<OperationJournalState> = when (this) {
        INTENT_RECORDED -> setOf(SENT, RECOVERY_REQUIRED)
        SENT -> setOf(
            ACKNOWLEDGED,
            UNCERTAIN,
            TERMINAL_SUCCEEDED,
            TERMINAL_REFUSED,
            TERMINAL_CONFLICT,
            RECOVERY_REQUIRED,
        )
        ACKNOWLEDGED -> setOf(
            UNCERTAIN,
            TERMINAL_SUCCEEDED,
            TERMINAL_REFUSED,
            TERMINAL_CONFLICT,
            RECOVERY_REQUIRED,
        )
        UNCERTAIN -> setOf(
            UNCERTAIN,
            TERMINAL_SUCCEEDED,
            TERMINAL_REFUSED,
            TERMINAL_CONFLICT,
            RECOVERY_REQUIRED,
        )
        TERMINAL_SUCCEEDED,
        TERMINAL_REFUSED,
        TERMINAL_CONFLICT,
        RECOVERY_REQUIRED -> emptySet()
    }

    fun canTransitionTo(target: OperationJournalState): Boolean =
        target in allowedNextStates()

    fun isTerminal(): Boolean =
        this == TERMINAL_SUCCEEDED ||
            this == TERMINAL_REFUSED ||
            this == TERMINAL_CONFLICT ||
            this == RECOVERY_REQUIRED

    companion object {
        fun requireValidTransition(
            from: OperationJournalState,
            to: OperationJournalState,
        ) {
            require(from.canTransitionTo(to)) {
                "Invalid journal state transition: $from -> $to"
            }
        }
    }
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
    val notes: String = "",
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
            notes,
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
            notes,
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
        notes = notes,
    )
}

@Serializable
data class OperationIntentExtra(
    val submissionId: String = "",
    val zipSha256: String = "",
    val reason: String = "",
    val targetStatus: String = "",
    val decision: String = "",
    val notes: String = "",
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
 * The encrypted columns of the Room operation journal (T3-202, PR3-010 `[REG+]`).
 *
 * Each constant carries the Room column name that holds the ciphertext. The column name is
 * part of the GCM associated data, so a ciphertext cannot be moved between columns, and the
 * record key is part of it, so a ciphertext cannot be moved between records.
 */
enum class JournalColumn(val columnName: String) {
    OPERATION("encryptedOperation"),
    PLAN("encryptedPlan"),
    TASK("encryptedTask"),
    BRANCH("encryptedBranch"),
    EXPECTED_HEAD("encryptedExpectedHead"),
    EXTRA_FIELDS("encryptedExtraFields"),
    AUDIT_REF("encryptedAuditRef"),
    RESULT_HEAD_SHA("encryptedResultHeadSha"),
    LAST_ERROR_CODE("encryptedLastErrorCode"),
}

/**
 * Derives the GCM associated data that binds a journal ciphertext to its record and column
 * (PR3-010 `[REG+]`).
 *
 * The associated data is a canonical, length-prefixed encoding of a fixed domain tag, a scheme
 * tag, the record key and the column name. Length prefixing keeps the encoding unambiguous: no
 * record key and column name combination can be confused with another, so the GCM tag check
 * fails closed whenever a ciphertext is replayed into a different record or a different column.
 *
 * The derivation is pure and deterministic: the same (record, column) pair always produces the
 * same bytes, on encryption and on decryption, including after a process restart.
 */
object JournalAssociatedData {

    /** Domain separator; prevents the derived bytes from being reused in another context. */
    private const val DOMAIN: String = "com.inscopelabs.buildengine.journal.field"

    /** Scheme tag; bumped if the derivation itself ever changes. */
    private const val SCHEME: String = "v1"

    /**
     * Associated data binding [recordKey] and [column].
     *
     * @throws IllegalArgumentException if [recordKey] is blank; a ciphertext must never be
     *   bound to an unidentified record.
     */
    fun forColumn(recordKey: String, column: JournalColumn): ByteArray {
        require(recordKey.isNotBlank()) { "Journal record key must not be blank" }
        val out = ByteArrayOutputStream()
        out.write(DOMAIN.toByteArray(Charsets.UTF_8))
        out.write(SCHEME.toByteArray(Charsets.UTF_8))
        out.writeLengthPrefixed(recordKey.toByteArray(Charsets.UTF_8))
        out.writeLengthPrefixed(column.columnName.toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLengthPrefixed(bytes: ByteArray) {
        write(ByteBuffer.allocate(4).putInt(bytes.size).array())
        write(bytes)
    }
}

/**
 * AES-256-GCM field-encryption mechanics for the operation journal, including the associated
 * data binding required by PR3-010 `[REG+]`.
 *
 * Split out from [AndroidKeystoreJournalCipher] so the wire format, the AAD binding and the
 * fail-closed error handling are exercised on the JVM with a JVM-generated key. The production
 * Keystore-backed cipher calls exactly these functions, so the JVM tests prove the production
 * behaviour rather than a parallel implementation.
 *
 * - A fresh random 12-byte IV is generated per encryption and prepended to the ciphertext.
 * - The associated data is authenticated by the GCM tag: decrypting with the wrong record or
 *   column fails closed with [JournalCryptoException].
 * - No error message contains plaintext.
 */
object JournalGcm {

    /**
     * Wire format version of an encrypted journal field.
     *
     * `v1` encrypted the field with no associated data and is therefore not readable under this
     * scheme; it is rejected as an unsupported format version rather than silently accepted.
     */
    const val FORMAT_PREFIX: String = "v2:"

    private const val TRANSFORMATION: String = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES: Int = 12
    private const val GCM_TAG_BITS: Int = 128
    private const val GCM_MIN_TAG_BYTES: Int = GCM_TAG_BITS / 8

    /**
     * Encrypts [plaintext] under [secretKey], authenticating [associatedData] with the GCM tag.
     */
    fun encryptToWire(secretKey: SecretKey, plaintext: String, associatedData: ByteArray): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.updateAAD(associatedData)
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

    /**
     * Decrypts [ciphertext] under [secretKey], requiring [associatedData] to match the data the
     * ciphertext was encrypted with.
     *
     * Fails closed with [JournalCryptoException] for an unsupported format version, a truncated
     * payload, a tampered ciphertext, or a record/column mismatch. The exception message never
     * contains plaintext.
     */
    fun decryptFromWire(secretKey: SecretKey, ciphertext: String, associatedData: ByteArray): String {
        return try {
            if (!ciphertext.startsWith(FORMAT_PREFIX)) {
                throw JournalCryptoException("Unsupported journal field format version")
            }
            val payload = Base64.getDecoder().decode(ciphertext.removePrefix(FORMAT_PREFIX))
            if (payload.size < GCM_IV_LENGTH_BYTES + GCM_MIN_TAG_BYTES) {
                throw JournalCryptoException("Encrypted journal field payload is truncated")
            }
            val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherBytes = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(associatedData)
            val decrypted = cipher.doFinal(cipherBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: JournalCryptoException) {
            throw e
        } catch (e: Exception) {
            throw JournalCryptoException("Failed to decrypt journal field", e)
        }
    }
}

/**
 * Field-level encryption abstraction for the Room operation journal (T3-202, PR3-010 `[REG+]`).
 *
 * Kept behind an interface so JVM tests can substitute a fake or simulated failing cipher.
 *
 * Every call must supply the associated data that binds the ciphertext to its record and
 * column (see [JournalAssociatedData]). The AAD parameter is deliberately not optional: a
 * call site that omits it must not compile.
 */
interface JournalFieldCipher {
    fun encrypt(plaintext: String, associatedData: ByteArray): String
    fun decrypt(ciphertext: String, associatedData: ByteArray): String
}

/**
 * AndroidKeyStore-backed AES-256-GCM field cipher using platform `javax.crypto` (T3-202).
 *
 * - Non-exportable AES key stored in `AndroidKeyStore`.
 * - Fresh random 12-byte IV generated per encrypted field (`setRandomizedEncryptionRequired(true)`).
 * - Ciphertext is bound to its record and column through GCM associated data (PR3-010 `[REG+]`).
 * - Fails closed by throwing [JournalCryptoException] on any key, IV, tag, or format error.
 */
class AndroidKeystoreJournalCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val keyStoreProvider: () -> KeyStore = {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    },
) : JournalFieldCipher {

    override fun encrypt(plaintext: String, associatedData: ByteArray): String =
        JournalGcm.encryptToWire(getOrCreateKey(), plaintext, associatedData)

    override fun decrypt(ciphertext: String, associatedData: ByteArray): String {
        val secretKey = getExistingKey()
            ?: throw JournalCryptoException("Journal encryption key '$keyAlias' is missing")
        return JournalGcm.decryptFromWire(secretKey, ciphertext, associatedData)
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
        private const val AES_KEY_BITS: Int = 256
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
            encryptedOperation = cipher.encrypt(intent.operation.name, aad(idempotencyKey, JournalColumn.OPERATION)),
            encryptedPlan = cipher.encrypt(intent.plan, aad(idempotencyKey, JournalColumn.PLAN)),
            encryptedTask = cipher.encrypt(intent.task, aad(idempotencyKey, JournalColumn.TASK)),
            encryptedBranch = cipher.encrypt(intent.branch, aad(idempotencyKey, JournalColumn.BRANCH)),
            encryptedExpectedHead = cipher.encrypt(
                intent.expectedHead,
                aad(idempotencyKey, JournalColumn.EXPECTED_HEAD),
            ),
            encryptedExtraFields = cipher.encrypt(extraJson, aad(idempotencyKey, JournalColumn.EXTRA_FIELDS)),
            state = OperationJournalState.INTENT_RECORDED,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        )
    }

    fun decryptRecord(
        entity: OperationJournalEntity,
        cipher: JournalFieldCipher,
    ): DecryptedJournalRecord {
        val recordKey = entity.idempotencyKey
        val opWire = cipher.decrypt(entity.encryptedOperation, aad(recordKey, JournalColumn.OPERATION))
        val op = OperationType.entries.firstOrNull { it.name == opWire }
            ?: throw JournalCryptoException("Unknown decrypted operation type")
        val plan = cipher.decrypt(entity.encryptedPlan, aad(recordKey, JournalColumn.PLAN))
        val task = cipher.decrypt(entity.encryptedTask, aad(recordKey, JournalColumn.TASK))
        val branch = cipher.decrypt(entity.encryptedBranch, aad(recordKey, JournalColumn.BRANCH))
        val expectedHead = cipher.decrypt(entity.encryptedExpectedHead, aad(recordKey, JournalColumn.EXPECTED_HEAD))
        val extraJson = cipher.decrypt(entity.encryptedExtraFields, aad(recordKey, JournalColumn.EXTRA_FIELDS))
        val extra = try {
            json.decodeFromString<OperationIntentExtra>(extraJson)
        } catch (e: Exception) {
            throw JournalCryptoException("Failed to decode decrypted extra fields", e)
        }
        val auditRef = entity.encryptedAuditRef?.let { cipher.decrypt(it, aad(recordKey, JournalColumn.AUDIT_REF)) }
        val resultHeadSha = entity.encryptedResultHeadSha?.let {
            cipher.decrypt(it, aad(recordKey, JournalColumn.RESULT_HEAD_SHA))
        }
        val lastErrorCode = entity.encryptedLastErrorCode?.let {
            cipher.decrypt(it, aad(recordKey, JournalColumn.LAST_ERROR_CODE))
        }

        val intent = OperationIntent(
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
            notes = extra.notes,
        )
        if (intent.fingerprint() != entity.intentFingerprint) {
            throw JournalCryptoException("Decrypted journal intent does not match stored intent fingerprint")
        }

        return DecryptedJournalRecord(
            idempotencyKey = entity.idempotencyKey,
            intent = intent,
            state = entity.state,
            createdAtEpochMs = entity.createdAtEpochMs,
            updatedAtEpochMs = entity.updatedAtEpochMs,
            auditRef = auditRef,
            resultHeadSha = resultHeadSha,
            lastErrorCode = lastErrorCode,
        )
    }

    /**
     * Associated data binding a ciphertext to its journal record and column (PR3-010 `[REG+]`).
     */
    private fun aad(recordKey: String, column: JournalColumn): ByteArray =
        JournalAssociatedData.forColumn(recordKey, column)
}
