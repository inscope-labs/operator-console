package com.inscopelabs.buildengine.testutil

import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.data.local.JournalCryptoException
import com.inscopelabs.buildengine.data.local.JournalFieldCipher
import com.inscopelabs.buildengine.data.local.OperationJournalDao
import com.inscopelabs.buildengine.data.local.OperationJournalEntity
import com.inscopelabs.buildengine.data.local.OperationJournalState
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import java.util.Base64

class InMemoryOperationJournalDao : OperationJournalDao {
    private val entries = linkedMapOf<String, OperationJournalEntity>()

    override suspend fun insert(entry: OperationJournalEntity) {
        require(!entries.containsKey(entry.idempotencyKey)) { "Duplicate primary key" }
        entries[entry.idempotencyKey] = entry
    }

    override suspend fun update(entry: OperationJournalEntity) {
        require(entries.containsKey(entry.idempotencyKey)) { "Missing entry to update" }
        entries[entry.idempotencyKey] = entry
    }

    override suspend fun findByKey(idempotencyKey: String): OperationJournalEntity? =
        entries[idempotencyKey]

    override suspend fun findByIntentFingerprintAndStates(
        intentFingerprint: String,
        states: List<OperationJournalState>,
    ): OperationJournalEntity? =
        entries.values
            .filter { it.intentFingerprint == intentFingerprint && it.state in states }
            .maxByOrNull { it.createdAtEpochMs }

    override suspend fun findByStates(states: List<OperationJournalState>): List<OperationJournalEntity> =
        entries.values
            .filter { it.state in states }
            .sortedBy { it.createdAtEpochMs }

    override suspend fun getAll(): List<OperationJournalEntity> =
        entries.values.sortedBy { it.createdAtEpochMs }
}

class RecordingFakeJournalCipher : JournalFieldCipher {
    var failOnDecrypt: Boolean = false
    val recordedPlaintexts = mutableListOf<String>()

    override fun encrypt(plaintext: String): String {
        recordedPlaintexts.add(plaintext)
        val encoded = Base64.getEncoder().encodeToString(plaintext.toByteArray(Charsets.UTF_8))
        return "fakeenc:$encoded"
    }

    override fun decrypt(ciphertext: String): String {
        if (failOnDecrypt || !ciphertext.startsWith("fakeenc:")) {
            throw JournalCryptoException("Simulated decrypt failure")
        }
        val bytes = Base64.getDecoder().decode(ciphertext.removePrefix("fakeenc:"))
        return String(bytes, Charsets.UTF_8)
    }
}

open class RecordingStage34OperationRepository(
    var defaultOutcome: OperationOutcome = OperationOutcome.Succeeded(
        auditRef = "audit-stage34-1",
        headSha = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
    ),
) : OperationRepository {
    val halts = mutableListOf<Triple<OperationRequest, String, SignedCapability>>()
    val resumes = mutableListOf<Triple<OperationRequest, String, SignedCapability>>()
    val reconciles = mutableListOf<Triple<OperationRequest, String, SignedCapability>>()
    val uploads = mutableListOf<UploadCall>()
    val statusTransitions = mutableListOf<Triple<OperationRequest, String, SignedCapability>>()
    val decides = mutableListOf<DecideCall>()
    var haltCause: String? = null

    data class UploadCall(
        val request: OperationRequest,
        val submissionId: String,
        val zipSha256: String,
        val zipBase64: String,
        val capability: SignedCapability,
    )

    data class DecideCall(
        val request: OperationRequest,
        val decision: String,
        val notes: String?,
        val capability: SignedCapability,
    )

    override suspend fun publish(request: OperationRequest, capability: SignedCapability): OperationOutcome =
        error("unused")

    override suspend fun upload(
        request: OperationRequest,
        submissionId: String,
        zipSha256: String,
        zipBase64: String,
        capability: SignedCapability,
    ): OperationOutcome {
        uploads += UploadCall(request, submissionId, zipSha256, zipBase64, capability)
        return defaultOutcome
    }

    override suspend fun reconcile(
        request: OperationRequest,
        submissionId: String,
        capability: SignedCapability,
    ): OperationOutcome {
        reconciles += Triple(request, submissionId, capability)
        return defaultOutcome
    }

    override suspend fun halt(
        request: OperationRequest,
        reason: String,
        capability: SignedCapability,
    ): OperationOutcome {
        halts += Triple(request, reason, capability)
        if (defaultOutcome is OperationOutcome.Succeeded) {
            haltCause = reason
        }
        return defaultOutcome
    }

    override suspend fun resume(
        request: OperationRequest,
        reason: String,
        capability: SignedCapability,
    ): OperationOutcome {
        resumes += Triple(request, reason, capability)
        if (defaultOutcome is OperationOutcome.Succeeded) {
            haltCause = null
        }
        return defaultOutcome
    }

    override suspend fun recordedHaltCause(): String? = haltCause

    override suspend fun setTaskStatus(
        request: OperationRequest,
        targetStatus: String,
        capability: SignedCapability,
    ): OperationOutcome {
        statusTransitions += Triple(request, targetStatus, capability)
        return defaultOutcome
    }

    override suspend fun decide(
        request: OperationRequest,
        decision: String,
        notes: String?,
        capability: SignedCapability,
    ): OperationOutcome {
        decides += DecideCall(request, decision, notes, capability)
        return defaultOutcome
    }
}
