package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayNotConfiguredException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.GatewayErrorDto
import com.inscopelabs.buildengine.core.network.dto.OperationLookupDto
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.core.time.SystemClock
import com.inscopelabs.buildengine.data.local.DecryptedJournalRecord
import com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator
import com.inscopelabs.buildengine.data.local.JournalCryptoException
import com.inscopelabs.buildengine.data.local.JournalFieldCipher
import com.inscopelabs.buildengine.data.local.OperationIntent
import com.inscopelabs.buildengine.data.local.OperationJournalCodec
import com.inscopelabs.buildengine.data.local.OperationJournalDao
import com.inscopelabs.buildengine.data.local.OperationJournalEntity
import com.inscopelabs.buildengine.data.local.OperationJournalState
import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.repository.OperationRepository
import com.inscopelabs.buildengine.util.Logger
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Journal-backed executor for privileged gateway operations (ADR 0009, ADR 0013, Stage 3.2).
 *
 * Guarantees:
 * - Every write intent and its 32-hex client idempotency key are encrypted and written to the
 *   Room journal BEFORE any network send (`INTENT_RECORDED` -> `SENT`). If the journal write or
 *   field encryption fails, nothing is sent.
 * - Repeated taps for the same non-terminal intent reuse the existing key and never create a
 *   second in-flight operation.
 * - Network loss or timeout on a write send marks the journal entry `UNCERTAIN` and returns
 *   [OperationOutcome.Timeout]; recovery always uses read-only `GET /operations/{id}` and never
 *   automatically resends a write.
 * - Capability strings, signatures, and capability nonces never enter the journal, cipher, or logs.
 */
class OperationRepositoryImpl(
    private val gateway: GatewayApi,
    private val journalDao: OperationJournalDao,
    private val cipher: JournalFieldCipher,
    private val keyGenerator: IdempotencyKeyGenerator = IdempotencyKeyGenerator(),
    private val clock: Clock = SystemClock(),
) : OperationRepository {

    private val intentLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Executes a consequential write operation behind the durable encrypted journal (T3-202, T3-204).
     *
     * [send] receives the validated 32-hex `idempotencyKey` and dispatches the wire request once.
     */
    suspend fun executeOperation(
        intent: OperationIntent,
        send: suspend (idempotencyKey: String) -> GatewayAckDto,
    ): OperationOutcome {
        val fingerprint = intent.fingerprint()
        val lock = intentLocks.computeIfAbsent(fingerprint) { Mutex() }
        return lock.withLock {
            val existing = journalDao.findByIntentFingerprintAndStates(fingerprint, IN_FLIGHT_STATES)
            if (existing != null) {
                return@withLock handleExistingInFlight(existing, send)
            }
            recordAndExecute(intent, send)
        }
    }

    /**
     * Looks up a single operation by [idempotencyKey] via `GET /operations/{id}` and updates any
     * matching journal entry according to docs/gateway-contract.md §4.3 (T3-203).
     */
    suspend fun lookupOperation(idempotencyKey: String): OperationOutcome {
        if (!IdempotencyKeyGenerator.isValid(idempotencyKey)) {
            return OperationOutcome.RecoveryRequired(
                idempotencyKey = idempotencyKey,
                code = CODE_IDEMPOTENCY_KEY_INVALID,
            )
        }
        val existing = journalDao.findByKey(idempotencyKey)
        val decrypted = if (existing != null) {
            decryptOrFailClosed(existing) ?: return OperationOutcome.RecoveryRequired(idempotencyKey)
        } else {
            null
        }
        return when (val result = resolveViaGatewayLookup(idempotencyKey, existing, decrypted)) {
            is LookupResolution.Resolved -> result.outcome
            is LookupResolution.TransientRetry -> result.fallbackOutcome
        }
    }

    /**
     * Resolves all journal entries in uncertain/post-send states using `GET /operations/{id}` only.
     * Never sends or resends a write operation (T3-203).
     */
    suspend fun recoverUncertainOperations(): RecoveryBatchSummary {
        val candidates = journalDao.findByStates(RECOVERY_CANDIDATE_STATES)
        var resolvedCount = 0
        var retryCount = 0
        val outcomes = LinkedHashMap<String, OperationOutcome>(candidates.size)

        for (entry in candidates) {
            val lock = intentLocks.computeIfAbsent(entry.intentFingerprint) { Mutex() }
            lock.withLock {
                val fresh = journalDao.findByKey(entry.idempotencyKey) ?: entry
                if (fresh.state !in RECOVERY_CANDIDATE_STATES) {
                    return@withLock
                }
                val decrypted = decryptOrFailClosed(fresh)
                if (decrypted == null) {
                    resolvedCount++
                    outcomes[fresh.idempotencyKey] = OperationOutcome.RecoveryRequired(fresh.idempotencyKey)
                    return@withLock
                }
                when (val resolution = resolveViaGatewayLookup(fresh.idempotencyKey, fresh, decrypted)) {
                    is LookupResolution.Resolved -> {
                        resolvedCount++
                        outcomes[fresh.idempotencyKey] = resolution.outcome
                    }
                    is LookupResolution.TransientRetry -> {
                        retryCount++
                        outcomes[fresh.idempotencyKey] = resolution.fallbackOutcome
                    }
                }
            }
        }

        return RecoveryBatchSummary(
            resolvedCount = resolvedCount,
            retryCount = retryCount,
            outcomes = outcomes,
        )
    }

    private suspend fun handleExistingInFlight(
        existing: OperationJournalEntity,
        send: suspend (idempotencyKey: String) -> GatewayAckDto,
    ): OperationOutcome {
        val decrypted = decryptOrFailClosed(existing)
            ?: return OperationOutcome.RecoveryRequired(existing.idempotencyKey)

        if (!IdempotencyKeyGenerator.isValid(existing.idempotencyKey)) {
            markRecoveryRequired(existing, code = CODE_IDEMPOTENCY_KEY_INVALID)
            return OperationOutcome.RecoveryRequired(
                idempotencyKey = existing.idempotencyKey,
                code = CODE_IDEMPOTENCY_KEY_INVALID,
            )
        }

        return when (existing.state) {
            OperationJournalState.INTENT_RECORDED -> {
                Logger.i(
                    TAG,
                    "Reusing recorded intent key=${existing.idempotencyKey} op=${decrypted.intent.operation.name}",
                )
                dispatchRecordedEntry(existing, send)
            }
            OperationJournalState.SENT,
            OperationJournalState.ACKNOWLEDGED,
            OperationJournalState.UNCERTAIN -> {
                Logger.i(
                    TAG,
                    "Suppressing duplicate write; looking up existing key=${existing.idempotencyKey} state=${existing.state}",
                )
                when (val resolution = resolveViaGatewayLookup(existing.idempotencyKey, existing, decrypted)) {
                    is LookupResolution.Resolved -> resolution.outcome
                    is LookupResolution.TransientRetry -> resolution.fallbackOutcome
                }
            }
            OperationJournalState.TERMINAL_SUCCEEDED,
            OperationJournalState.TERMINAL_REFUSED,
            OperationJournalState.TERMINAL_CONFLICT,
            OperationJournalState.RECOVERY_REQUIRED -> {
                OperationOutcome.RecoveryRequired(existing.idempotencyKey)
            }
        }
    }

    private suspend fun recordAndExecute(
        intent: OperationIntent,
        send: suspend (idempotencyKey: String) -> GatewayAckDto,
    ): OperationOutcome {
        val key = try {
            keyGenerator.generate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to generate idempotency key; aborting before send", e)
            return OperationOutcome.RecoveryRequired(idempotencyKey = "")
        }

        val nowMs = nowEpochMillis()
        val initialEntity = try {
            OperationJournalCodec.encryptNewEntity(
                idempotencyKey = key,
                intent = intent,
                cipher = cipher,
                nowEpochMs = nowMs,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Journal encryption failed for key=$key; nothing sent", e)
            return OperationOutcome.RecoveryRequired(idempotencyKey = key)
        }

        try {
            journalDao.insert(initialEntity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Journal write failed for key=$key; nothing sent", e)
            return OperationOutcome.RecoveryRequired(idempotencyKey = key)
        }

        Logger.i(TAG, "Journal recorded intent key=$key op=${intent.operation.name}")
        return dispatchRecordedEntry(initialEntity, send)
    }

    private suspend fun dispatchRecordedEntry(
        recordedEntity: OperationJournalEntity,
        send: suspend (idempotencyKey: String) -> GatewayAckDto,
    ): OperationOutcome {
        val key = recordedEntity.idempotencyKey
        val sentEntity = recordedEntity.copy(
            state = OperationJournalState.SENT,
            updatedAtEpochMs = nowEpochMillis(),
        )
        try {
            journalDao.update(sentEntity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to transition key=$key to SENT; aborting send", e)
            return OperationOutcome.RecoveryRequired(idempotencyKey = key)
        }

        return try {
            val ack = send(key)
            handleWriteAcknowledgement(sentEntity, ack)
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Write send coroutine timed out for key=$key; marking UNCERTAIN")
            markUncertain(sentEntity)
            OperationOutcome.Timeout(idempotencyKey = key)
        } catch (e: UnknownHostException) {
            Logger.w(TAG, "Write send unknown host for key=$key; marking UNCERTAIN")
            markUncertain(sentEntity)
            OperationOutcome.Timeout(idempotencyKey = key)
        } catch (e: IOException) {
            Logger.w(TAG, "Write send IO failure for key=$key; marking UNCERTAIN")
            markUncertain(sentEntity)
            OperationOutcome.Timeout(idempotencyKey = key)
        } catch (e: GatewayNotConfiguredException) {
            Logger.w(TAG, "Gateway not configured for key=$key")
            markTerminalRefusal(sentEntity, OperationJournalState.TERMINAL_REFUSED, CODE_NOT_CONFIGURED, null)
            OperationOutcome.Refused(code = CODE_NOT_CONFIGURED)
        } catch (e: GatewayProtocolException) {
            Logger.w(TAG, "Malformed gateway write response for key=$key; marking UNCERTAIN")
            markUncertain(sentEntity)
            OperationOutcome.UncertainCompletion(idempotencyKey = key)
        } catch (e: GatewayHttpException) {
            handleWriteHttpException(sentEntity, e)
        } catch (e: JournalCryptoException) {
            Logger.e(TAG, "Journal encryption error after send for key=$key", e)
            markRecoveryRequired(sentEntity, code = null)
            OperationOutcome.RecoveryRequired(idempotencyKey = key)
        }
    }

    private suspend fun handleWriteAcknowledgement(
        sentEntity: OperationJournalEntity,
        ack: GatewayAckDto,
    ): OperationOutcome {
        val key = sentEntity.idempotencyKey
        val nowMs = nowEpochMillis()
        val encryptedAudit = ack.auditRef.takeIf { it.isNotBlank() }?.let { cipher.encrypt(it) }
        val encryptedHead = ack.headSha.takeIf { it.isNotBlank() }?.let { cipher.encrypt(it) }

        val ackedEntity = sentEntity.copy(
            state = OperationJournalState.ACKNOWLEDGED,
            updatedAtEpochMs = nowMs,
            encryptedAuditRef = encryptedAudit ?: sentEntity.encryptedAuditRef,
            encryptedResultHeadSha = encryptedHead ?: sentEntity.encryptedResultHeadSha,
        )
        journalDao.update(ackedEntity)

        if (!ack.accepted || ack.auditRef.isBlank() || ack.headSha.isBlank()) {
            markUncertain(ackedEntity)
            return OperationOutcome.UncertainCompletion(
                idempotencyKey = key,
                headSha = ack.headSha.takeIf { it.isNotBlank() },
                auditRef = ack.auditRef.takeIf { it.isNotBlank() },
            )
        }

        journalDao.update(
            ackedEntity.copy(
                state = OperationJournalState.TERMINAL_SUCCEEDED,
                updatedAtEpochMs = nowEpochMillis(),
            ),
        )
        Logger.i(TAG, "Operation key=$key acknowledged and recorded TERMINAL_SUCCEEDED")
        return OperationOutcome.Succeeded(
            auditRef = ack.auditRef,
            headSha = ack.headSha,
        )
    }

    private suspend fun handleWriteHttpException(
        sentEntity: OperationJournalEntity,
        error: GatewayHttpException,
    ): OperationOutcome {
        val key = sentEntity.idempotencyKey
        if (error.statusCode == HTTP_GATEWAY_TIMEOUT) {
            markUncertain(sentEntity)
            return OperationOutcome.Timeout(idempotencyKey = key)
        }
        val rawCode = parseErrorCode(error.body)
        if (rawCode == null) {
            markRecoveryRequired(sentEntity, code = null)
            return OperationOutcome.RecoveryRequired(idempotencyKey = key, code = null)
        }
        val baseCode = normalizeCode(rawCode)
        if (baseCode !in KNOWN_GATEWAY_CODES) {
            Logger.w(TAG, "Unknown gateway refusal code on write for key=$key; failing closed")
            markRecoveryRequired(sentEntity, code = null)
            return OperationOutcome.RecoveryRequired(idempotencyKey = key, code = null)
        }
        return if (baseCode in CONFLICT_CODES) {
            markTerminalRefusal(sentEntity, OperationJournalState.TERMINAL_CONFLICT, rawCode, null)
            OperationOutcome.Conflict(code = rawCode)
        } else {
            markTerminalRefusal(sentEntity, OperationJournalState.TERMINAL_REFUSED, rawCode, null)
            OperationOutcome.Refused(code = rawCode)
        }
    }

    private suspend fun resolveViaGatewayLookup(
        key: String,
        existingEntity: OperationJournalEntity?,
        decryptedRecord: DecryptedJournalRecord?,
    ): LookupResolution {
        val lookup = try {
            gateway.lookupOperation(key)
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Lookup timed out for key=$key; leaving journal entry unchanged")
            return LookupResolution.TransientRetry(uncertainFromRecord(key, decryptedRecord))
        } catch (e: UnknownHostException) {
            Logger.w(TAG, "Lookup network host unavailable for key=$key; leaving journal entry unchanged")
            return LookupResolution.TransientRetry(uncertainFromRecord(key, decryptedRecord))
        } catch (e: IOException) {
            Logger.w(TAG, "Lookup network failure for key=$key; leaving journal entry unchanged")
            return LookupResolution.TransientRetry(uncertainFromRecord(key, decryptedRecord))
        } catch (e: GatewayNotConfiguredException) {
            Logger.w(TAG, "Gateway not configured during lookup for key=$key; leaving entry unchanged")
            return LookupResolution.TransientRetry(uncertainFromRecord(key, decryptedRecord))
        } catch (e: GatewayProtocolException) {
            Logger.w(TAG, "Malformed lookup response for key=$key; failing closed")
            existingEntity?.let { markRecoveryRequired(it, code = null) }
            return LookupResolution.Resolved(OperationOutcome.RecoveryRequired(idempotencyKey = key))
        } catch (e: GatewayHttpException) {
            return handleLookupHttpException(key, existingEntity, decryptedRecord, e)
        }

        return mapLookupDtoToOutcome(key, existingEntity, decryptedRecord, lookup)
    }

    private suspend fun handleLookupHttpException(
        key: String,
        existingEntity: OperationJournalEntity?,
        decryptedRecord: DecryptedJournalRecord?,
        error: GatewayHttpException,
    ): LookupResolution {
        if (error.statusCode in TRANSIENT_HTTP_STATUSES) {
            Logger.w(TAG, "Lookup hit transient HTTP ${error.statusCode} for key=$key; entry unchanged")
            return LookupResolution.TransientRetry(uncertainFromRecord(key, decryptedRecord))
        }
        val parsedCode = parseErrorCode(error.body)
        if (error.statusCode == HTTP_NOT_FOUND) {
            val effectiveCode = parsedCode ?: CODE_OPERATION_NOT_FOUND
            existingEntity?.let { markRecoveryRequired(it, code = effectiveCode) }
            Logger.w(TAG, "Lookup 404 ($effectiveCode) for key=$key -> RecoveryRequired")
            return LookupResolution.Resolved(
                OperationOutcome.RecoveryRequired(
                    idempotencyKey = key,
                    code = effectiveCode,
                ),
            )
        }
        val safeCode = parsedCode?.takeIf { normalizeCode(it) in KNOWN_GATEWAY_CODES }
        existingEntity?.let { markRecoveryRequired(it, code = safeCode) }
        Logger.w(TAG, "Lookup HTTP ${error.statusCode} for key=$key -> RecoveryRequired")
        return LookupResolution.Resolved(
            OperationOutcome.RecoveryRequired(
                idempotencyKey = key,
                code = safeCode,
            ),
        )
    }

    private suspend fun mapLookupDtoToOutcome(
        key: String,
        existingEntity: OperationJournalEntity?,
        decryptedRecord: DecryptedJournalRecord?,
        lookup: OperationLookupDto,
    ): LookupResolution {
        val status = lookup.status.trim().lowercase()
        val auditRef = lookup.auditRef?.trim()?.takeIf { it.isNotEmpty() }
        val headSha = lookup.headSha?.trim()?.takeIf { it.isNotEmpty() }

        return when (status) {
            STATUS_COMPLETED -> {
                if (auditRef == null || headSha == null) {
                    Logger.w(TAG, "Lookup completed missing auditRef or headSha for key=$key; failing closed")
                    existingEntity?.let { markRecoveryRequired(it, code = null) }
                    return LookupResolution.Resolved(
                        OperationOutcome.RecoveryRequired(
                            idempotencyKey = key,
                            auditRef = auditRef,
                        ),
                    )
                }
                val branch = lookup.branch?.trim()?.takeIf { it.isNotEmpty() }
                    ?: decryptedRecord?.intent?.branch?.trim()?.takeIf { it.isNotEmpty() }
                if (branch == null) {
                    Logger.w(TAG, "Lookup completed without branch for live-head check on key=$key")
                    existingEntity?.let { markUncertainWithResult(it, headSha = headSha, auditRef = auditRef) }
                    return LookupResolution.Resolved(
                        OperationOutcome.UncertainCompletion(
                            idempotencyKey = key,
                            headSha = headSha,
                            auditRef = auditRef,
                        ),
                    )
                }

                val liveHead = try {
                    gateway.branchHead(branch)
                } catch (e: TimeoutCancellationException) {
                    Logger.w(TAG, "Live-head check timed out for key=$key; leaving entry unchanged")
                    return LookupResolution.TransientRetry(
                        OperationOutcome.UncertainCompletion(key, headSha = headSha, auditRef = auditRef),
                    )
                } catch (e: UnknownHostException) {
                    Logger.w(TAG, "Live-head check host unreachable for key=$key; leaving entry unchanged")
                    return LookupResolution.TransientRetry(
                        OperationOutcome.UncertainCompletion(key, headSha = headSha, auditRef = auditRef),
                    )
                } catch (e: IOException) {
                    Logger.w(TAG, "Live-head check IO error for key=$key; leaving entry unchanged")
                    return LookupResolution.TransientRetry(
                        OperationOutcome.UncertainCompletion(key, headSha = headSha, auditRef = auditRef),
                    )
                } catch (e: GatewayHttpException) {
                    if (e.statusCode in TRANSIENT_HTTP_STATUSES) {
                        Logger.w(TAG, "Live-head check HTTP ${e.statusCode} for key=$key; entry unchanged")
                        return LookupResolution.TransientRetry(
                            OperationOutcome.UncertainCompletion(key, headSha = headSha, auditRef = auditRef),
                        )
                    }
                    Logger.w(TAG, "Live-head check HTTP ${e.statusCode} for key=$key -> UncertainCompletion")
                    existingEntity?.let { markUncertainWithResult(it, headSha = headSha, auditRef = auditRef) }
                    return LookupResolution.Resolved(
                        OperationOutcome.UncertainCompletion(
                            idempotencyKey = key,
                            headSha = headSha,
                            auditRef = auditRef,
                        ),
                    )
                } catch (e: GatewayProtocolException) {
                    Logger.w(TAG, "Live-head check malformed response for key=$key -> UncertainCompletion")
                    existingEntity?.let { markUncertainWithResult(it, headSha = headSha, auditRef = auditRef) }
                    return LookupResolution.Resolved(
                        OperationOutcome.UncertainCompletion(
                            idempotencyKey = key,
                            headSha = headSha,
                            auditRef = auditRef,
                        ),
                    )
                }

                val liveSha = liveHead.headSha.trim()
                if (liveSha.isNotEmpty() && liveSha.equals(headSha, ignoreCase = true)) {
                    val persisted = if (existingEntity != null) {
                        markTerminalSucceeded(existingEntity, auditRef = auditRef, headSha = headSha)
                    } else {
                        true
                    }
                    if (!persisted) {
                        return LookupResolution.Resolved(
                            OperationOutcome.RecoveryRequired(idempotencyKey = key, auditRef = auditRef),
                        )
                    }
                    LookupResolution.Resolved(
                        OperationOutcome.Succeeded(
                            auditRef = auditRef,
                            headSha = headSha,
                        ),
                    )
                } else {
                    Logger.w(TAG, "Live branch head drifted from completed headSha for key=$key -> UncertainCompletion")
                    existingEntity?.let { markUncertainWithResult(it, headSha = headSha, auditRef = auditRef) }
                    LookupResolution.Resolved(
                        OperationOutcome.UncertainCompletion(
                            idempotencyKey = key,
                            headSha = headSha,
                            auditRef = auditRef,
                        ),
                    )
                }
            }

            STATUS_PENDING -> {
                existingEntity?.let { markUncertainWithResult(it, headSha = headSha, auditRef = auditRef) }
                LookupResolution.Resolved(
                    OperationOutcome.UncertainCompletion(
                        idempotencyKey = key,
                        headSha = headSha,
                        auditRef = auditRef,
                    ),
                )
            }

            STATUS_REFUSED -> {
                val rawCode = lookup.code?.trim()?.takeIf { it.isNotEmpty() }
                if (rawCode == null) {
                    Logger.w(TAG, "Lookup refused missing code for key=$key; failing closed")
                    existingEntity?.let { markRecoveryRequired(it, code = null, auditRef = auditRef) }
                    return LookupResolution.Resolved(
                        OperationOutcome.RecoveryRequired(
                            idempotencyKey = key,
                            code = null,
                            auditRef = auditRef,
                        ),
                    )
                }
                val baseCode = normalizeCode(rawCode)
                if (baseCode !in KNOWN_GATEWAY_CODES) {
                    Logger.w(TAG, "Lookup refused unknown code for key=$key; failing closed")
                    existingEntity?.let { markRecoveryRequired(it, code = null, auditRef = auditRef) }
                    return LookupResolution.Resolved(
                        OperationOutcome.RecoveryRequired(
                            idempotencyKey = key,
                            code = null,
                            auditRef = auditRef,
                        ),
                    )
                }
                if (baseCode in CONFLICT_CODES) {
                    val persisted = existingEntity?.let {
                        markTerminalRefusal(it, OperationJournalState.TERMINAL_CONFLICT, rawCode, auditRef)
                    } ?: true
                    if (!persisted) {
                        return LookupResolution.Resolved(
                            OperationOutcome.RecoveryRequired(idempotencyKey = key, auditRef = auditRef),
                        )
                    }
                    LookupResolution.Resolved(
                        OperationOutcome.Conflict(
                            code = rawCode,
                            auditRef = auditRef,
                        ),
                    )
                } else {
                    val persisted = existingEntity?.let {
                        markTerminalRefusal(it, OperationJournalState.TERMINAL_REFUSED, rawCode, auditRef)
                    } ?: true
                    if (!persisted) {
                        return LookupResolution.Resolved(
                            OperationOutcome.RecoveryRequired(idempotencyKey = key, auditRef = auditRef),
                        )
                    }
                    LookupResolution.Resolved(
                        OperationOutcome.Refused(
                            code = rawCode,
                            auditRef = auditRef,
                        ),
                    )
                }
            }

            STATUS_FAILED -> {
                val rawCode = lookup.code?.trim()?.takeIf { it.isNotEmpty() }
                val safeCode = rawCode?.takeIf { normalizeCode(it) in KNOWN_GATEWAY_CODES }
                existingEntity?.let { markRecoveryRequired(it, code = safeCode, auditRef = auditRef) }
                LookupResolution.Resolved(
                    OperationOutcome.RecoveryRequired(
                        idempotencyKey = key,
                        code = safeCode,
                        auditRef = auditRef,
                    ),
                )
            }

            else -> {
                Logger.w(TAG, "Unknown lookup status for key=$key; failing closed")
                existingEntity?.let { markRecoveryRequired(it, code = null, auditRef = auditRef) }
                LookupResolution.Resolved(
                    OperationOutcome.RecoveryRequired(
                        idempotencyKey = key,
                        code = null,
                        auditRef = auditRef,
                    ),
                )
            }
        }
    }

    private suspend fun decryptOrFailClosed(entity: OperationJournalEntity): DecryptedJournalRecord? =
        try {
            OperationJournalCodec.decryptRecord(entity, cipher)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Journal decryption failed for key=${entity.idempotencyKey}; failing closed", e)
            runCatching {
                journalDao.update(
                    entity.copy(
                        state = OperationJournalState.RECOVERY_REQUIRED,
                        updatedAtEpochMs = nowEpochMillis(),
                    ),
                )
            }
            null
        }

    private suspend fun markUncertain(entity: OperationJournalEntity) {
        runCatching {
            journalDao.update(
                entity.copy(
                    state = OperationJournalState.UNCERTAIN,
                    updatedAtEpochMs = nowEpochMillis(),
                ),
            )
        }
    }

    private suspend fun markUncertainWithResult(
        entity: OperationJournalEntity,
        headSha: String?,
        auditRef: String?,
    ) {
        try {
            val encHead = headSha?.let { cipher.encrypt(it) } ?: entity.encryptedResultHeadSha
            val encAudit = auditRef?.let { cipher.encrypt(it) } ?: entity.encryptedAuditRef
            journalDao.update(
                entity.copy(
                    state = OperationJournalState.UNCERTAIN,
                    updatedAtEpochMs = nowEpochMillis(),
                    encryptedResultHeadSha = encHead,
                    encryptedAuditRef = encAudit,
                ),
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Encryption failed updating uncertain state for key=${entity.idempotencyKey}", e)
            markRecoveryRequired(entity, code = null, auditRef = null)
        }
    }

    private suspend fun markTerminalSucceeded(
        entity: OperationJournalEntity,
        auditRef: String,
        headSha: String,
    ): Boolean = try {
        journalDao.update(
            entity.copy(
                state = OperationJournalState.TERMINAL_SUCCEEDED,
                updatedAtEpochMs = nowEpochMillis(),
                encryptedAuditRef = cipher.encrypt(auditRef),
                encryptedResultHeadSha = cipher.encrypt(headSha),
            ),
        )
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Encryption failed recording terminal success for key=${entity.idempotencyKey}", e)
        markRecoveryRequired(entity, code = null, auditRef = null)
        false
    }

    private suspend fun markTerminalRefusal(
        entity: OperationJournalEntity,
        state: OperationJournalState,
        code: String,
        auditRef: String?,
    ): Boolean = try {
        val encCode = cipher.encrypt(code)
        val encAudit = auditRef?.let { cipher.encrypt(it) } ?: entity.encryptedAuditRef
        journalDao.update(
            entity.copy(
                state = state,
                updatedAtEpochMs = nowEpochMillis(),
                encryptedLastErrorCode = encCode,
                encryptedAuditRef = encAudit,
            ),
        )
        true
    } catch (e: Exception) {
        Logger.e(TAG, "Encryption failed recording refusal for key=${entity.idempotencyKey}", e)
        markRecoveryRequired(entity, code = null, auditRef = null)
        false
    }

    private suspend fun markRecoveryRequired(
        entity: OperationJournalEntity,
        code: String?,
        auditRef: String? = null,
    ) {
        val encCode = code?.let { runCatching { cipher.encrypt(it) }.getOrNull() }
            ?: entity.encryptedLastErrorCode
        val encAudit = auditRef?.let { runCatching { cipher.encrypt(it) }.getOrNull() }
            ?: entity.encryptedAuditRef
        runCatching {
            journalDao.update(
                entity.copy(
                    state = OperationJournalState.RECOVERY_REQUIRED,
                    updatedAtEpochMs = nowEpochMillis(),
                    encryptedLastErrorCode = encCode,
                    encryptedAuditRef = encAudit,
                ),
            )
        }
    }

    private fun uncertainFromRecord(
        key: String,
        record: DecryptedJournalRecord?,
    ): OperationOutcome.UncertainCompletion = OperationOutcome.UncertainCompletion(
        idempotencyKey = key,
        headSha = record?.resultHeadSha,
        auditRef = record?.auditRef,
    )

    private fun parseErrorCode(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val dto = try {
            errorJson.decodeFromString<GatewayErrorDto>(body)
        } catch (_: Exception) {
            return null
        }
        return dto.effectiveCode.trim().takeIf { it.isNotEmpty() }
    }

    private fun normalizeCode(code: String): String =
        code.trim().uppercase().substringBefore(':').trim()

    private fun nowEpochMillis(): Long = clock.now().toEpochMilli()

    private sealed interface LookupResolution {
        data class Resolved(val outcome: OperationOutcome) : LookupResolution
        data class TransientRetry(val fallbackOutcome: OperationOutcome) : LookupResolution
    }

    data class RecoveryBatchSummary(
        val resolvedCount: Int,
        val retryCount: Int,
        val outcomes: Map<String, OperationOutcome>,
    )

    // -- Privileged operation use-case facades (completed in Stages 3.3 to 3.5) --

    override suspend fun publish(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: publish — the only path to the public repository.")

    override suspend fun upload(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: upload a submission (content-addressed, immutable).")

    override suspend fun reconcile(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: reconcile after the operator's review.")

    override suspend fun halt(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: halt — repository-wide stop, separately scoped.")

    override suspend fun resume(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: resume — clears the halt marker.")

    override suspend fun decide(request: OperationRequest): OperationResult =
        TODO("SCAFFOLD: decide — the highest-authority action in the system.")

    companion object {
        private const val TAG = "OperationJournal"

        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_PENDING = "pending"
        private const val STATUS_REFUSED = "refused"
        private const val STATUS_FAILED = "failed"

        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GATEWAY_TIMEOUT = 504
        private val TRANSIENT_HTTP_STATUSES = setOf(500, 502, 503, 504)

        const val CODE_IDEMPOTENCY_KEY_INVALID = "IDEMPOTENCY_KEY_INVALID"
        const val CODE_IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"
        const val CODE_OPERATION_NOT_FOUND = "OPERATION_NOT_FOUND"
        private const val CODE_NOT_CONFIGURED = "NOT_CONFIGURED"

        val IN_FLIGHT_STATES: List<OperationJournalState> = listOf(
            OperationJournalState.INTENT_RECORDED,
            OperationJournalState.SENT,
            OperationJournalState.ACKNOWLEDGED,
            OperationJournalState.UNCERTAIN,
        )

        val RECOVERY_CANDIDATE_STATES: List<OperationJournalState> = listOf(
            OperationJournalState.SENT,
            OperationJournalState.ACKNOWLEDGED,
            OperationJournalState.UNCERTAIN,
        )

        /** Exact conflict set from docs/gateway-contract.md §4.3. */
        val CONFLICT_CODES: Set<String> = setOf(
            ErrorMapper.GATEWAY_HEAD_DRIFT,
            ErrorMapper.GATEWAY_HEAD_MOVED,
            ErrorMapper.GATEWAY_NONCE_REPLAYED,
            ErrorMapper.GATEWAY_SUBMISSION_IMMUTABLE,
            ErrorMapper.GATEWAY_ANCESTRY_FAIL,
            ErrorMapper.GATEWAY_RECONCILE_REQUIRED,
            CODE_IDEMPOTENCY_KEY_REUSED,
        )

        /** All contract §4.1 error codes (including Revision 1.1 BD-1.7 codes). */
        val KNOWN_GATEWAY_CODES: Set<String> = ErrorMapper.GATEWAY_CODES + setOf(
            CODE_IDEMPOTENCY_KEY_INVALID,
            CODE_IDEMPOTENCY_KEY_REUSED,
            CODE_OPERATION_NOT_FOUND,
        )

        private val errorJson = Json { ignoreUnknownKeys = true }
    }
}
