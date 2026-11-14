package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.BranchHeadDto
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.network.dto.OperationLookupDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator
import com.inscopelabs.buildengine.data.local.JournalCryptoException
import com.inscopelabs.buildengine.data.local.JournalFieldCipher
import com.inscopelabs.buildengine.data.local.OperationIntent
import com.inscopelabs.buildengine.data.local.OperationJournalDao
import com.inscopelabs.buildengine.data.local.OperationJournalEntity
import com.inscopelabs.buildengine.data.local.OperationJournalState
import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.testutil.FakeGatewayApi
import com.inscopelabs.buildengine.util.Logger
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive JVM test suite for T3-201 to T3-205:
 * - pre-send journal write & crash recovery
 * - post-send crash recovery (lookup only, zero resends)
 * - process death & restart recovery
 * - network timeout on send (Timeout -> lookup) and on lookup (entry unchanged)
 * - full contract §4.3 outcome mapping table (including 404, unknown status, missing/unknown code, head drift)
 * - replay refusal (NONCE_REPLAYED -> Conflict)
 * - head drift (HEAD_DRIFT / HEAD_MOVED -> Conflict; completed + moved live head -> UncertainCompletion)
 * - idempotent repeated taps (same key reused, zero second send) vs different intent (new key)
 * - cipher failure fails closed to RecoveryRequired
 * - zero capability, signature, or nonce persisted in journal entity/cipher inputs or emitted in logs
 */
class OperationJournalExecutorTest {

    private lateinit var dao: FakeOperationJournalDao
    private lateinit var cipher: RecordingFakeCipher
    private lateinit var clock: FixedClock
    private lateinit var keyGenerator: IdempotencyKeyGenerator
    private lateinit var fakeGateway: FakeGatewayApi
    private val capturedLogs = mutableListOf<String>()

    @Before
    fun setUp() {
        dao = FakeOperationJournalDao()
        cipher = RecordingFakeCipher()
        clock = FixedClock(1761840000L)
        keyGenerator = IdempotencyKeyGenerator(SecureRandomSource())
        fakeGateway = FakeGatewayApi().apply { branchHeadResult = { HEAD_SHA_1 } }
        capturedLogs.clear()
        Logger.logSink = { _, tag, message, error ->
            capturedLogs.add("$tag: $message ${error?.message.orEmpty()}")
        }
    }

    @After
    fun tearDown() {
        Logger.logSink = null
    }

    private fun newExecutor(gateway: GatewayApi = fakeGateway): OperationRepositoryImpl =
        OperationRepositoryImpl(
            gateway = gateway,
            journalDao = dao,
            cipher = cipher,
            keyGenerator = keyGenerator,
            clock = clock,
        )

    private fun sampleIntent(
        op: OperationType = OperationType.PUBLISH,
        plan: String = "atlas",
        task: String = "10008",
        branch: String = "task-10008-refactor-parser",
        expectedHead: String = HEAD_SHA_1,
        submissionId: String = "",
    ): OperationIntent = OperationIntent(
        operation = op,
        plan = plan,
        task = task,
        branch = branch,
        expectedHead = expectedHead,
        submissionId = submissionId,
    )

    // -- T3-201 & G-08: outcome model & state separation ------------------------

    @Test
    fun `Succeeded cannot be constructed without non-blank auditRef and headSha`() {
        val missingAudit = runCatching { OperationOutcome.Succeeded(auditRef = " ", headSha = HEAD_SHA_1) }
        val missingHead = runCatching { OperationOutcome.Succeeded(auditRef = "audit-1", headSha = "") }
        assertTrue(missingAudit.isFailure)
        assertTrue(missingHead.isFailure)
    }

    @Test
    fun `G-08 accepted ack without auditRef or headSha never reports Succeeded`() = runTest {
        val repo = newExecutor()
        val outcome = repo.executeOperation(sampleIntent()) { key ->
            GatewayAckDto(
                accepted = true,
                operation = "PUBLISH",
                branch = "task-10008-refactor-parser",
                headSha = "",
                auditRef = "",
            )
        }

        assertTrue(outcome is OperationOutcome.UncertainCompletion)
        val entry = dao.getAll().single()
        assertEquals(OperationJournalState.UNCERTAIN, entry.state)
    }

    // -- T3-202: crash between journal write and send ---------------------------

    @Test
    fun `crash between journal write and send sends nothing and leaves recoverable entry`() = runTest {
        val repo = newExecutor()
        var sendCalls = 0
        dao.onAfterInsert = {
            throw SimulatedProcessCrash("crashed right after journal insert, before send")
        }

        val thrown = runCatching {
            repo.executeOperation(sampleIntent()) {
                sendCalls++
                GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "audit-1")
            }
        }.exceptionOrNull()

        assertTrue(thrown is SimulatedProcessCrash)
        assertEquals(0, sendCalls)

        val entries = dao.getAll()
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(OperationJournalState.INTENT_RECORDED, entry.state)
        assertTrue(KEY_PATTERN.matches(entry.idempotencyKey))

        // Recovering via fresh tap reuses the same key and sends exactly once.
        dao.onAfterInsert = null
        val freshRepo = newExecutor()
        var sentKey: String? = null
        val outcome = freshRepo.executeOperation(sampleIntent()) { key ->
            sendCalls++
            sentKey = key
            GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "audit-ok")
        }

        assertEquals(1, sendCalls)
        assertEquals(entry.idempotencyKey, sentKey)
        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-ok", headSha = HEAD_SHA_1), outcome)
        assertEquals(1, dao.getAll().size)
        assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.getAll().single().state)
    }

    // -- T3-202 & T3-203: crash between send and acknowledgement & restart ------

    @Test
    fun `crash between send and ack marks SENT and restart recovery resolves via lookup with zero resend`() = runTest {
        var writeCount = 0
        var lookupCount = 0
        val intent = sampleIntent()
        val gateway = object : GatewayApi by fakeGateway {
            override suspend fun publish(request: PublishRequestDto): GatewayAckDto {
                writeCount++
                throw SimulatedProcessCrash("killed while awaiting HTTP response")
            }

            override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto {
                lookupCount++
                return OperationLookupDto(
                    id = idempotencyKey,
                    operation = "PUBLISH",
                    status = "completed",
                    branch = "task-10008-refactor-parser",
                    headSha = HEAD_SHA_1,
                    auditRef = "audit-recovered-1",
                    timestamp = 1761840010L,
                )
            }

            override suspend fun branchHead(branch: String): BranchHeadDto =
                BranchHeadDto(branch = branch, headSha = HEAD_SHA_1)
        }

        val instance1 = newExecutor(gateway)
        runCatching {
            instance1.executeOperation(intent) { key ->
                gateway.publish(
                    PublishRequestDto(
                        plan = intent.plan,
                        task = intent.task,
                        branch = intent.branch,
                        head = intent.expectedHead,
                        capability = FAKE_CAPABILITY,
                        idempotencyKey = key,
                    ),
                )
            }
        }
        assertEquals(1, writeCount)
        assertEquals(0, lookupCount)
        val persistedAfterCrash = dao.getAll().single()
        assertEquals(OperationJournalState.SENT, persistedAfterCrash.state)

        // Simulate process death & restart with a fresh repository reading the same persistent DAO & cipher.
        val instance2 = newExecutor(gateway)
        val summary = instance2.recoverUncertainOperations()

        assertEquals(1, writeCount) // NEVER resent!
        assertEquals(1, lookupCount)
        assertEquals(1, summary.resolvedCount)
        assertEquals(0, summary.retryCount)
        assertEquals(
            OperationOutcome.Succeeded(auditRef = "audit-recovered-1", headSha = HEAD_SHA_1),
            summary.outcomes[persistedAfterCrash.idempotencyKey],
        )
        assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.getAll().single().state)
    }

    // -- T3-203 & T3-205: network loss on send & on lookup ----------------------

    @Test
    fun `network loss or timeout on write send returns Timeout then lookup resolves without resend`() = runTest {
        var writeCount = 0
        var lookupCount = 0
        val gateway = object : GatewayApi by fakeGateway {
            override suspend fun publish(request: PublishRequestDto): GatewayAckDto {
                writeCount++
                throw SocketTimeoutException("read timed out after send")
            }

            override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto {
                lookupCount++
                return OperationLookupDto(
                    id = idempotencyKey,
                    operation = "PUBLISH",
                    status = "completed",
                    branch = "task-10008-refactor-parser",
                    headSha = HEAD_SHA_1,
                    auditRef = "audit-after-timeout",
                    timestamp = 1761840020L,
                )
            }

            override suspend fun branchHead(branch: String): BranchHeadDto =
                BranchHeadDto(branch = branch, headSha = HEAD_SHA_1)
        }

        val repo = newExecutor(gateway)
        val sendOutcome = repo.executeOperation(sampleIntent()) { key ->
            gateway.publish(PublishRequestDto(idempotencyKey = key))
        }

        assertTrue(sendOutcome is OperationOutcome.Timeout)
        val key = (sendOutcome as OperationOutcome.Timeout).idempotencyKey
        assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)

        val recovery = repo.recoverUncertainOperations()
        assertEquals(1, writeCount)
        assertEquals(1, lookupCount)
        assertEquals(1, recovery.resolvedCount)
        assertEquals(0, recovery.retryCount)
        assertEquals(
            OperationOutcome.Succeeded(auditRef = "audit-after-timeout", headSha = HEAD_SHA_1),
            recovery.outcomes[key],
        )
    }

    @Test
    fun `network failure or timeout on lookup leaves journal entry unchanged and signals retry`() = runTest {
        val gateway = object : GatewayApi by fakeGateway {
            override suspend fun publish(request: PublishRequestDto): GatewayAckDto =
                throw IOException("connection reset")

            override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto =
                throw IOException("network offline during lookup")
        }

        val repo = newExecutor(gateway)
        val sendOutcome = repo.executeOperation(sampleIntent()) { key ->
            gateway.publish(PublishRequestDto(idempotencyKey = key))
        }
        val key = (sendOutcome as OperationOutcome.Timeout).idempotencyKey
        val beforeRecovery = dao.findByKey(key)!!
        assertEquals(OperationJournalState.UNCERTAIN, beforeRecovery.state)

        clock.advanceSeconds(30)
        val summary = repo.recoverUncertainOperations()

        assertEquals(0, summary.resolvedCount)
        assertEquals(1, summary.retryCount)
        val afterRecovery = dao.findByKey(key)!!
        assertEquals(beforeRecovery, afterRecovery)
    }

    // -- Replay & head drift on direct write & lookup ---------------------------

    @Test
    fun `replay refusal NONCE_REPLAYED maps to Conflict`() = runTest {
        val repo = newExecutor()
        val outcome = repo.executeOperation(sampleIntent()) {
            throw GatewayHttpException(
                statusCode = 409,
                body = """{"code": "NONCE_REPLAYED", "message": "nonce already spent"}""",
                message = "409 conflict",
            )
        }

        assertEquals(OperationOutcome.Conflict(code = "NONCE_REPLAYED"), outcome)
        assertEquals(OperationJournalState.TERMINAL_CONFLICT, dao.getAll().single().state)
    }

    @Test
    fun `head drift HEAD_DRIFT and HEAD_MOVED on write map to Conflict`() = runTest {
        val repo = newExecutor()

        val driftOutcome = repo.executeOperation(sampleIntent(task = "10008")) {
            throw GatewayHttpException(
                statusCode = 409,
                body = """{"code": "HEAD_DRIFT", "message": "head drifted"}""",
                message = "409",
            )
        }
        assertEquals(OperationOutcome.Conflict(code = "HEAD_DRIFT"), driftOutcome)

        val movedOutcome = repo.executeOperation(sampleIntent(task = "10009")) {
            throw GatewayHttpException(
                statusCode = 409,
                body = """{"code": "HEAD_MOVED", "message": "head moved"}""",
                message = "409",
            )
        }
        assertEquals(OperationOutcome.Conflict(code = "HEAD_MOVED"), movedOutcome)
    }

    // -- Contract §4.3 complete lookup mapping table ----------------------------

    @Test
    fun `lookup completed with matching live head maps to Succeeded`() = runTest {
        val key = seedUncertainEntry()
        val repo = newExecutor(
            lookupGateway(
                lookupResponse = {
                    OperationLookupDto(
                        id = it,
                        operation = "PUBLISH",
                        status = "completed",
                        branch = "task-10008-refactor-parser",
                        headSha = HEAD_SHA_1,
                        auditRef = "audit-comp-1",
                        timestamp = 1761840050L,
                    )
                },
                liveHeadSha = HEAD_SHA_1,
            ),
        )

        val outcome = repo.lookupOperation(key)

        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-comp-1", headSha = HEAD_SHA_1), outcome)
        assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(key)?.state)
    }

    @Test
    fun `lookup completed with moved live head maps to UncertainCompletion`() = runTest {
        val key = seedUncertainEntry()
        val repo = newExecutor(
            lookupGateway(
                lookupResponse = {
                    OperationLookupDto(
                        id = it,
                        operation = "PUBLISH",
                        status = "completed",
                        branch = "task-10008-refactor-parser",
                        headSha = HEAD_SHA_1,
                        auditRef = "audit-comp-moved",
                        timestamp = 1761840050L,
                    )
                },
                liveHeadSha = HEAD_SHA_2,
            ),
        )

        val outcome = repo.lookupOperation(key)

        assertEquals(
            OperationOutcome.UncertainCompletion(
                idempotencyKey = key,
                headSha = HEAD_SHA_1,
                auditRef = "audit-comp-moved",
            ),
            outcome,
        )
        assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)
    }

    @Test
    fun `lookup pending maps to UncertainCompletion`() = runTest {
        val key = seedUncertainEntry()
        val repo = newExecutor(
            lookupGateway(
                lookupResponse = {
                    OperationLookupDto(
                        id = it,
                        operation = "PUBLISH",
                        status = "pending",
                        timestamp = 1761840050L,
                    )
                },
            ),
        )

        val outcome = repo.lookupOperation(key)

        assertEquals(OperationOutcome.UncertainCompletion(idempotencyKey = key), outcome)
        assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)
    }

    @Test
    fun `lookup refused with every section 4_3 conflict code maps to Conflict`() = runTest {
        val conflictCodes = listOf(
            "HEAD_DRIFT",
            "HEAD_MOVED",
            "NONCE_REPLAYED",
            "SUBMISSION_IMMUTABLE",
            "ANCESTRY_FAIL",
            "RECONCILE_REQUIRED",
            "IDEMPOTENCY_KEY_REUSED",
        )

        conflictCodes.forEachIndexed { index, code ->
            dao = FakeOperationJournalDao()
            val key = seedUncertainEntry(task = "1000$index")
            val repo = newExecutor(
                lookupGateway(
                    lookupResponse = {
                        OperationLookupDto(
                            id = it,
                            operation = "PUBLISH",
                            status = "refused",
                            auditRef = "audit-c-$index",
                            code = code,
                            timestamp = 1761840050L,
                        )
                    },
                ),
            )

            val outcome = repo.lookupOperation(key)
            assertEquals(
                "Expected Conflict for code $code",
                OperationOutcome.Conflict(code = code, auditRef = "audit-c-$index"),
                outcome,
            )
            assertEquals(OperationJournalState.TERMINAL_CONFLICT, dao.findByKey(key)?.state)
        }
    }

    @Test
    fun `lookup refused with non-conflict codes including HALT_ACTIVE maps to Refused`() = runTest {
        val refusalCodes = listOf("HALT_ACTIVE", "BRANCH_MISMATCH", "DENIED_PATH", "GUARD_FAILED")

        refusalCodes.forEachIndexed { index, code ->
            dao = FakeOperationJournalDao()
            val key = seedUncertainEntry(task = "2000$index")
            val repo = newExecutor(
                lookupGateway(
                    lookupResponse = {
                        OperationLookupDto(
                            id = it,
                            operation = "PUBLISH",
                            status = "refused",
                            auditRef = "audit-r-$index",
                            code = code,
                            timestamp = 1761840050L,
                        )
                    },
                ),
            )

            val outcome = repo.lookupOperation(key)
            assertEquals(
                "Expected Refused for code $code",
                OperationOutcome.Refused(code = code, auditRef = "audit-r-$index"),
                outcome,
            )
            assertEquals(OperationJournalState.TERMINAL_REFUSED, dao.findByKey(key)?.state)
        }
    }

    @Test
    fun `lookup failed maps to RecoveryRequired`() = runTest {
        val key = seedUncertainEntry()
        val repo = newExecutor(
            lookupGateway(
                lookupResponse = {
                    OperationLookupDto(
                        id = it,
                        operation = "PUBLISH",
                        status = "failed",
                        code = "NETWORK",
                        auditRef = "audit-fail-1",
                        timestamp = 1761840050L,
                    )
                },
            ),
        )

        val outcome = repo.lookupOperation(key)

        assertEquals(
            OperationOutcome.RecoveryRequired(
                idempotencyKey = key,
                code = "NETWORK",
                auditRef = "audit-fail-1",
            ),
            outcome,
        )
        assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)
    }

    @Test
    fun `lookup 404 OPERATION_NOT_FOUND maps to RecoveryRequired and never resends`() = runTest {
        var writeCount = 0
        val key = seedUncertainEntry()
        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun publish(request: PublishRequestDto): GatewayAckDto {
                    writeCount++
                    return GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "unused")
                }

                override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto {
                    throw GatewayHttpException(
                        statusCode = 404,
                        body = """{"code": "OPERATION_NOT_FOUND", "message": "not found"}""",
                        message = "404",
                    )
                }
            },
        )

        val summary = repo.recoverUncertainOperations()

        assertEquals(0, writeCount)
        assertEquals(1, summary.resolvedCount)
        assertEquals(
            OperationOutcome.RecoveryRequired(
                idempotencyKey = key,
                code = "OPERATION_NOT_FOUND",
            ),
            summary.outcomes[key],
        )
        assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)
    }

    @Test
    fun `lookup with unknown status or missing or unknown code fails closed to RecoveryRequired`() = runTest {
        val cases = listOf(
            OperationLookupDto(id = "", operation = "PUBLISH", status = "mystery_status", timestamp = 1L),
            OperationLookupDto(id = "", operation = "PUBLISH", status = "refused", code = null, timestamp = 1L),
            OperationLookupDto(id = "", operation = "PUBLISH", status = "refused", code = "UNKNOWN_NEW_CODE", timestamp = 1L),
            OperationLookupDto(id = "", operation = "PUBLISH", status = "completed", auditRef = null, headSha = HEAD_SHA_1, timestamp = 1L),
        )

        cases.forEachIndexed { idx, dtoTemplate ->
            dao = FakeOperationJournalDao()
            val key = seedUncertainEntry(task = "3000$idx")
            val repo = newExecutor(
                lookupGateway(lookupResponse = { dtoTemplate.copy(id = it) }),
            )

            val outcome = repo.lookupOperation(key)
            assertTrue("Case $idx must fail closed to RecoveryRequired, was $outcome", outcome is OperationOutcome.RecoveryRequired)
            assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)
        }
    }

    // -- T3-204: Idempotency (repeated taps reuse key; distinct intents get new keys) --

    @Test
    fun `repeated taps while operation is in flight reuse one key and never send a second write`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        val sentKeys = mutableListOf<String>()

        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto =
                    OperationLookupDto(
                        id = idempotencyKey,
                        operation = "PUBLISH",
                        status = "pending",
                        timestamp = 1761840000L,
                    )
            },
        )

        val intent = sampleIntent()
        // Wait: seed an already SENT / UNCERTAIN tap, AND test concurrent second tap and sequential tap.
        // First simulate Tap 1 that times out (leaving UNCERTAIN with key1), then Tap 2 arrives for same intent:
        var writeCalls = 0
        val timeoutRepo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto =
                    OperationLookupDto(
                        id = idempotencyKey,
                        operation = "PUBLISH",
                        status = "completed",
                        branch = intent.branch,
                        headSha = HEAD_SHA_1,
                        auditRef = "audit-idempotent-1",
                        timestamp = 1761840090L,
                    )

                override suspend fun branchHead(branch: String): BranchHeadDto =
                    BranchHeadDto(branch = branch, headSha = HEAD_SHA_1)
            },
        )

        val tap1 = timeoutRepo.executeOperation(intent) { key ->
            writeCalls++
            sentKeys.add(key)
            throw IOException("lost response")
        }
        assertTrue(tap1 is OperationOutcome.Timeout)
        assertEquals(1, writeCalls)

        // Tap 2 for exact same intent must NOT create a second journal row or send a second write.
        val tap2 = timeoutRepo.executeOperation(intent) { key ->
            writeCalls++
            sentKeys.add(key)
            GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "should-not-send")
        }
        assertEquals(1, writeCalls)
        assertEquals(1, dao.getAll().size)
        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-idempotent-1", headSha = HEAD_SHA_1), tap2)

        // Different intent gets a different idempotencyKey.
        val tap3 = timeoutRepo.executeOperation(sampleIntent(task = "10099")) { key ->
            writeCalls++
            sentKeys.add(key)
            GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "audit-other-intent")
        }
        assertEquals(2, writeCalls)
        assertEquals(2, dao.getAll().size)
        assertEquals(2, sentKeys.toSet().size)
        assertNotEquals(sentKeys[0], sentKeys[1])
        assertTrue(tap3 is OperationOutcome.Succeeded)
    }

    @Test
    fun `concurrent double tap for one intent reuses one key and executes single write`() = runTest {
        val started = CompletableDeferred<Unit>()
        val allowFinish = CompletableDeferred<Unit>()
        var writeCalls = 0
        val sentKeys = mutableListOf<String>()
        val repo = newExecutor(
            lookupGateway(
                lookupResponse = { key ->
                    OperationLookupDto(
                        id = key,
                        operation = "PUBLISH",
                        status = "completed",
                        branch = "task-10008-refactor-parser",
                        headSha = HEAD_SHA_1,
                        auditRef = "audit-concurrent-1",
                        timestamp = 1761840100L,
                    )
                },
                liveHeadSha = HEAD_SHA_1,
            ),
        )

        val intent = sampleIntent()
        val first = async {
            repo.executeOperation(intent) { key ->
                writeCalls++
                sentKeys.add(key)
                started.complete(Unit)
                allowFinish.await()
                throw IOException("first call timed out")
            }
        }
        started.await()
        val second = async {
            repo.executeOperation(intent) { key ->
                writeCalls++
                sentKeys.add(key)
                GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "unexpected-second-send")
            }
        }
        allowFinish.complete(Unit)

        val firstOutcome = first.await()
        val secondOutcome = second.await()

        assertEquals(1, writeCalls)
        assertEquals(1, dao.getAll().size)
        assertEquals(1, sentKeys.size)
        assertTrue(firstOutcome is OperationOutcome.Timeout)
        assertEquals(
            OperationOutcome.Succeeded(auditRef = "audit-concurrent-1", headSha = HEAD_SHA_1),
            secondOutcome,
        )
    }

    // -- Cipher & journal failure fail-closed guarantees -----------------------

    @Test
    fun `journal insert failure before send aborts and sends nothing`() = runTest {
        dao.failOnInsert = true
        var writeCalls = 0
        val repo = newExecutor()

        val outcome = repo.executeOperation(sampleIntent()) {
            writeCalls++
            GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "audit-x")
        }

        assertEquals(0, writeCalls)
        assertEquals(0, dao.getAll().size)
        assertTrue(outcome is OperationOutcome.RecoveryRequired)
    }

    @Test
    fun `encryption failure before send aborts and sends nothing`() = runTest {
        cipher.failOnEncrypt = true
        var writeCalls = 0
        val repo = newExecutor()

        val outcome = repo.executeOperation(sampleIntent()) {
            writeCalls++
            GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "audit-x")
        }

        assertEquals(0, writeCalls)
        assertEquals(0, dao.getAll().size)
        assertTrue(outcome is OperationOutcome.RecoveryRequired)
    }

    @Test
    fun `decryption failure during recovery fails closed to RecoveryRequired and never returns default`() = runTest {
        val key = seedUncertainEntry()
        cipher.failOnDecrypt = true
        var lookupCalls = 0
        val repo = newExecutor(
            lookupGateway(
                lookupResponse = {
                    lookupCalls++
                    OperationLookupDto(
                        id = it,
                        operation = "PUBLISH",
                        status = "completed",
                        branch = "task-10008-refactor-parser",
                        headSha = HEAD_SHA_1,
                        auditRef = "audit-1",
                        timestamp = 1761840000L,
                    )
                },
            ),
        )

        val summary = repo.recoverUncertainOperations()

        assertEquals(0, lookupCalls)
        assertEquals(1, summary.resolvedCount)
        assertEquals(OperationOutcome.RecoveryRequired(idempotencyKey = key), summary.outcomes[key])
        assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)
    }

    // -- Zero capability / signature / nonce persistence & logging ---------------

    @Test
    fun `no capability, signature, or nonce ever enters journal entity, cipher inputs, or logs`() = runTest {
        val secretNonce = "7f3c9b1e5d2a7046"
        val secretSig = "sig_base64url_raw_rs_secret_value_0123456789"
        val fullCapability =
            "ctxop-v1\nk2\n1761840000\norder-7731\natlas\n10008\nPUBLISH\n" +
                "task-10008-refactor-parser\n$HEAD_SHA_1\n$secretNonce\n$secretSig"

        val repo = newExecutor()
        val intent = sampleIntent()
        val outcome = repo.executeOperation(intent) { key ->
            val request = PublishRequestDto(
                plan = intent.plan,
                task = intent.task,
                branch = intent.branch,
                head = intent.expectedHead,
                capability = fullCapability,
                idempotencyKey = key,
            )
            // Idempotency key is never the capability nonce.
            assertNotEquals(secretNonce, request.idempotencyKey)
            GatewayAckDto(
                accepted = true,
                operation = "PUBLISH",
                branch = intent.branch,
                headSha = HEAD_SHA_1,
                auditRef = "audit-clean-1",
            )
        }
        assertTrue(outcome is OperationOutcome.Succeeded)

        val stored = dao.getAll().single()
        val serializedEntityText = stored.toString()
        val allPlaintextCipherInputs = cipher.recordedPlaintexts.joinToString("\n")
        val allLogOutput = capturedLogs.joinToString("\n")

        val forbiddenSecrets = listOf("ctxop-v1", fullCapability, secretNonce, secretSig)
        for (forbidden in forbiddenSecrets) {
            assertFalse("Entity must not contain $forbidden", serializedEntityText.contains(forbidden))
            assertFalse("Cipher input must not contain $forbidden", allPlaintextCipherInputs.contains(forbidden))
            assertFalse("Logs must not contain $forbidden", allLogOutput.contains(forbidden))
        }
        assertFalse(stored.encryptedOperation.contains("PUBLISH"))
        assertFalse(stored.encryptedBranch.contains("task-10008"))
    }

    // -- Stage 3.3: T3-302 live-head check on the synchronous acknowledgement path ----------

    @Test
    fun `T3-302 accepted ack with a drifted live head is UncertainCompletion and is not resent`() = runTest {
        var sends = 0
        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun branchHead(branch: String): BranchHeadDto =
                    BranchHeadDto(branch = branch, headSha = HEAD_SHA_2)
            },
        )
        val outcome = repo.executeOperation(sampleIntent()) {
            sends++
            GatewayAckDto(accepted = true, branch = "task-10008-refactor-parser", headSha = HEAD_SHA_1, auditRef = "audit-drift")
        }

        assertEquals(
            OperationOutcome.UncertainCompletion(
                idempotencyKey = dao.getAll().single().idempotencyKey,
                headSha = HEAD_SHA_1,
                auditRef = "audit-drift",
            ),
            outcome,
        )
        assertEquals(1, sends)
        assertEquals(OperationJournalState.UNCERTAIN, dao.getAll().single().state)
    }

    @Test
    fun `T3-302 accepted ack with an unavailable live-head check is UncertainCompletion`() = runTest {
        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun branchHead(branch: String): BranchHeadDto =
                    throw IOException("live head unreachable")
            },
        )
        val outcome = repo.executeOperation(sampleIntent()) {
            GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "audit-unavailable")
        }
        assertTrue(outcome is OperationOutcome.UncertainCompletion)
        assertEquals(OperationJournalState.UNCERTAIN, dao.getAll().single().state)
    }

    @Test
    fun `T3-302 accepted ack naming a different branch is UncertainCompletion`() = runTest {
        val repo = newExecutor()
        val outcome = repo.executeOperation(sampleIntent()) {
            GatewayAckDto(accepted = true, branch = "task-99999-other", headSha = HEAD_SHA_1, auditRef = "audit-other")
        }
        assertTrue(outcome is OperationOutcome.UncertainCompletion)
        assertEquals(OperationJournalState.UNCERTAIN, dao.getAll().single().state)
    }

    @Test
    fun `T3-302 accepted ack with a matching live head is Succeeded`() = runTest {
        val repo = newExecutor()
        val outcome = repo.executeOperation(sampleIntent()) {
            GatewayAckDto(accepted = true, branch = "task-10008-refactor-parser", headSha = HEAD_SHA_1, auditRef = "audit-match")
        }
        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-match", headSha = HEAD_SHA_1), outcome)
        assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.getAll().single().state)
    }

    // -- Stage 3.3: publish through the journal-backed executor ------------------------------

    private fun signedPublish(task: String = "10008"): Pair<OperationRequest, SignedCapability> {
        val scope = CapabilityScope(
            op = OperationType.PUBLISH,
            branch = "task-10008-refactor-parser",
            head = HEAD_SHA_1,
            nonce = SECRET_NONCE,
        )
        val minter = CapabilityMinter(
            signer = HmacCapabilitySigner(keyId = "test_kid", secretUtf8 = "test-secret-key-32-bytes-long!!"),
            nonceSource = DeviceNonceSource(SecureRandomSource()),
            clock = clock,
        )
        val signed = minter.mint(scope = scope, order = "order-7731", plan = "atlas", task = task)
        val request = OperationRequest(
            op = OperationType.PUBLISH,
            scope = scope,
            plan = "atlas",
            task = task,
            order = "order-7731",
        )
        return request to signed
    }

    @Test
    fun `publish sends the capability on the wire only and persists or logs no secret`() = runTest {
        var sent: PublishRequestDto? = null
        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun publish(request: PublishRequestDto): GatewayAckDto {
                    sent = request
                    return GatewayAckDto(
                        accepted = true,
                        operation = "PUBLISH",
                        branch = "task-10008-refactor-parser",
                        headSha = HEAD_SHA_1,
                        auditRef = "audit-pub-1",
                    )
                }
            },
        )
        val (request, signed) = signedPublish()

        val outcome = repo.publish(request, signed)

        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-pub-1", headSha = HEAD_SHA_1), outcome)
        val wire = sent!!
        assertEquals(signed.toWire(), wire.capability)
        assertEquals("atlas", wire.plan)
        assertEquals("10008", wire.task)
        assertEquals("task-10008-refactor-parser", wire.branch)
        assertEquals(HEAD_SHA_1, wire.head)
        assertTrue(KEY_PATTERN.matches(wire.idempotencyKey))
        assertEquals(dao.getAll().single().idempotencyKey, wire.idempotencyKey)
        assertNotEquals(SECRET_NONCE, wire.idempotencyKey)

        val persisted = dao.getAll().single().toString() + cipher.recordedPlaintexts.joinToString("|")
        assertFalse(persisted.contains(SECRET_NONCE))
        assertFalse(persisted.contains(signed.signatureBase64Url))
        assertFalse(persisted.contains("ctxop-v1"))
        capturedLogs.forEach { line ->
            assertFalse(line.contains(SECRET_NONCE))
            assertFalse(line.contains(signed.signatureBase64Url))
            assertFalse(line.contains("ctxop-v1"))
        }
    }

    @Test
    fun `publish rejects a capability that does not match the request before any journal write or send`() = runTest {
        var sends = 0
        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun publish(request: PublishRequestDto): GatewayAckDto {
                    sends++
                    return GatewayAckDto(accepted = true)
                }
            },
        )
        val (request, signed) = signedPublish()
        val mismatched = request.copy(task = "99999")

        val failure = runCatching { repo.publish(mismatched, signed) }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, sends)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `publish refusals keep their distinct meaning`() = runTest {
        val cases = listOf(
            "HALT_ACTIVE" to OperationOutcome.Refused(code = "HALT_ACTIVE"),
            "BRANCH_MISMATCH" to OperationOutcome.Refused(code = "BRANCH_MISMATCH"),
            "NONCE_REPLAYED" to OperationOutcome.Conflict(code = "NONCE_REPLAYED"),
            "HEAD_DRIFT" to OperationOutcome.Conflict(code = "HEAD_DRIFT"),
        )
        cases.forEachIndexed { index, (code, expected) ->
            dao = FakeOperationJournalDao()
            val repo = newExecutor(
                object : GatewayApi by fakeGateway {
                    override suspend fun publish(request: PublishRequestDto): GatewayAckDto =
                        throw GatewayHttpException(
                            statusCode = 409,
                            body = """{"code": "$code", "message": "refused"}""",
                            message = "refused",
                        )
                },
            )
            val (request, signed) = signedPublish(task = "10008")
            assertEquals("case $index $code", expected, repo.publish(request, signed))
        }
    }

    @Test
    fun `publish repeated after a lost response looks up and never resends`() = runTest {
        var sends = 0
        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun publish(request: PublishRequestDto): GatewayAckDto {
                    sends++
                    throw IOException("lost response")
                }

                override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto =
                    OperationLookupDto(
                        id = idempotencyKey,
                        operation = "PUBLISH",
                        status = "completed",
                        branch = "task-10008-refactor-parser",
                        headSha = HEAD_SHA_1,
                        auditRef = "audit-lookup",
                        timestamp = 1761840090L,
                    )
            },
        )
        val (request, signed) = signedPublish()

        val first = repo.publish(request, signed)
        assertTrue(first is OperationOutcome.Timeout)
        assertEquals(1, sends)

        val (request2, signed2) = signedPublish()
        val second = repo.publish(request2, signed2)

        assertEquals(1, sends)
        assertEquals(1, dao.getAll().size)
        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-lookup", headSha = HEAD_SHA_1), second)
    }

    // -- Helpers & Fakes --------------------------------------------------------

    private suspend fun seedUncertainEntry(task: String = "10008"): String {
        val repo = newExecutor(
            object : GatewayApi by fakeGateway {
                override suspend fun publish(request: PublishRequestDto): GatewayAckDto =
                    throw IOException("simulated disconnect")
            },
        )
        val outcome = repo.executeOperation(sampleIntent(task = task)) { key ->
            throw IOException("simulated disconnect for $key")
        }
        return (outcome as OperationOutcome.Timeout).idempotencyKey
    }

    private fun lookupGateway(
        lookupResponse: (String) -> OperationLookupDto,
        liveHeadSha: String = HEAD_SHA_1,
    ): GatewayApi = object : GatewayApi by fakeGateway {
        override suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto =
            lookupResponse(idempotencyKey)

        override suspend fun branchHead(branch: String): BranchHeadDto =
            BranchHeadDto(branch = branch, headSha = liveHeadSha)
    }

    private class SimulatedProcessCrash(message: String) : CancellationException(message)

    private class FakeOperationJournalDao : OperationJournalDao {
        private val entries = linkedMapOf<String, OperationJournalEntity>()
        var onAfterInsert: (() -> Unit)? = null
        var failOnInsert: Boolean = false

        override suspend fun insert(entry: OperationJournalEntity) {
            if (failOnInsert) throw IllegalStateException("Simulated SQLite write error")
            require(!entries.containsKey(entry.idempotencyKey)) { "Duplicate primary key" }
            entries[entry.idempotencyKey] = entry
            onAfterInsert?.invoke()
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

    private class RecordingFakeCipher : JournalFieldCipher {
        var failOnEncrypt: Boolean = false
        var failOnDecrypt: Boolean = false
        val recordedPlaintexts = mutableListOf<String>()

        override fun encrypt(plaintext: String): String {
            if (failOnEncrypt) throw JournalCryptoException("Simulated key/encrypt failure")
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

    private companion object {
        const val HEAD_SHA_1 = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
        const val HEAD_SHA_2 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val FAKE_CAPABILITY =
            "ctxop-v1\nk2\n1761840000\norder-7731\natlas\n10008\nPUBLISH\n" +
                "task-10008-refactor-parser\n$HEAD_SHA_1\n7f3c9b1e5d2a7046\nsig_fake"
        const val SECRET_NONCE = "7f3c9b1e5d2a7046"
        val KEY_PATTERN = Regex("^[0-9a-f]{32}$")
    }
}
