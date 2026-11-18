package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
import com.inscopelabs.buildengine.capability.codec.OperatorCapabilityCodec
import com.inscopelabs.buildengine.capability.model.CapabilityScope
import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.capability.signing.HmacCapabilitySigner
import com.inscopelabs.buildengine.capability.signing.SignedCapability
import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.dto.GatewayAckDto
import com.inscopelabs.buildengine.core.time.FixedClock
import com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator
import com.inscopelabs.buildengine.data.local.OperationIntent
import com.inscopelabs.buildengine.data.local.OperationJournalCodec
import com.inscopelabs.buildengine.data.local.OperationJournalEntity
import com.inscopelabs.buildengine.data.local.OperationJournalState
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.testutil.InMemoryOperationJournalDao
import com.inscopelabs.buildengine.testutil.RecordingFakeJournalCipher
import com.inscopelabs.buildengine.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.util.Base64
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Audit and gap-closing JVM test suite for PR3-012 (`T3-204`; `I-05`):
 * - Bullet 1: Same user intent reuses its existing key across every unresolved state (including
 *   `ACKNOWLEDGED`), while terminal states issue a new key on a fresh operator attempt.
 * - Bullet 2: Materially different operation/scope/fields (including length-prefix boundary cases,
 *   upload `zipSha256` normalization, and `decide` `notes` / F1) produce distinct fingerprints and
 *   keys even while the prior intent is active.
 * - Bullet 3: Reusing a key with a different fingerprint (`409 IDEMPOTENCY_KEY_REUSED` on write and
 *   refused lookup, plus local `@PrimaryKey` collision before send / F2) returns `Conflict` and is
 *   never dispatched as a new write.
 * - Bullet 4: Process restart across `INTENT_RECORDED`, `SENT`, `ACKNOWLEDGED`, and `UNCERTAIN`
 *   (including repeated restarts with differently seeded [IdempotencyKeyGenerator]s) keeps the
 *   existing key and never generates a new one.
 * - Bullet 5: Idempotency keys and capability nonces remain strictly distinct on a shared CSPRNG, on
 *   the HTTP wire, across re-minted capabilities, in `GET /operations/{id}`, and in the journal.
 * - F4: Stored `intentFingerprint` is verified against decrypted fields before dispatch and recovery.
 */
class IdempotencyAndFingerprintTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var gatewayRepository: GatewayRepository
    private lateinit var clock: FixedClock

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = HttpClient(OkHttp) {
            expectSuccess = false
            followRedirects = false
            engine {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                    retryOnConnectionFailure(false)
                    connectTimeout(2, TimeUnit.SECONDS)
                    readTimeout(2, TimeUnit.SECONDS)
                    writeTimeout(2, TimeUnit.SECONDS)
                }
            }
        }
        gatewayRepository = GatewayRepository(
            httpClient = GatewayHttpClient(
                client = httpClient,
                baseUrlProvider = { server.url("/").toString() },
            ),
            errorMapper = ErrorMapper(),
        )
        clock = FixedClock(1761840000L)
    }

    @After
    fun tearDown() {
        Logger.logSink = null
        httpClient.close()
        runCatching { server.shutdown() }
    }

    // -- Bullet 1 & Bullet 2: Fingerprinting, unresolved reuse, terminal renewal --

    @Test
    fun `bullet 2 OperationIntent fingerprint is deterministic and sensitive to every field and boundary`() {
        val base = OperationIntent(
            operation = OperationType.PUBLISH,
            plan = "atlas",
            task = "10008",
            branch = BRANCH,
            expectedHead = HEAD_SHA_1,
            submissionId = "sub-1",
            zipSha256 = ZIP_SHA_1,
            reason = "none",
            targetStatus = "completed",
            decision = "APPROVED",
            notes = "rationale-1",
        )
        val equalCopy = base.copy()

        assertEquals(64, base.fingerprint().length)
        assertTrue(SHA256_HEX.matches(base.fingerprint()))
        assertEquals(base.fingerprint(), equalCopy.fingerprint())

        val singleFieldMutations = listOf(
            base.copy(operation = OperationType.RECONCILE),
            base.copy(plan = "atlas-2"),
            base.copy(task = "10009"),
            base.copy(branch = "task-10008-other"),
            base.copy(expectedHead = HEAD_SHA_2),
            base.copy(submissionId = "sub-2"),
            base.copy(zipSha256 = ZIP_SHA_2),
            base.copy(reason = "changed-reason"),
            base.copy(targetStatus = "in_progress"),
            base.copy(decision = "REJECTED"),
            base.copy(notes = "rationale-2"),
        )
        val allFingerprints = listOf(base.fingerprint()) + singleFieldMutations.map { it.fingerprint() }
        assertEquals(
            "Mutating any of the 11 fingerprint fields must produce a distinct digest",
            12,
            allFingerprints.toSet().size,
        )

        // Length-prefix canonicalization must prevent concatenation collisions across adjacent fields.
        val pairA1 = base.copy(plan = "ab", task = "c")
        val pairA2 = base.copy(plan = "a", task = "bc")
        assertNotEquals(pairA1.fingerprint(), pairA2.fingerprint())

        val pairB1 = base.copy(submissionId = "sub", zipSha256 = "12")
        val pairB2 = base.copy(submissionId = "su", zipSha256 = "b12")
        assertNotEquals(pairB1.fingerprint(), pairB2.fingerprint())

        val pairC1 = base.copy(decision = "AB", notes = "C")
        val pairC2 = base.copy(decision = "A", notes = "BC")
        assertNotEquals(pairC1.fingerprint(), pairC2.fingerprint())
    }

    @Test
    fun `bullet 2 materially different operation, head, submission, or zipSha256 gets distinct keys while prior intent is unresolved`() =
        runTest {
            val dao = InMemoryOperationJournalDao()
            val repo = newRepository(dao = dao, keyGenerator = incrementingKeyGenerator(start = 1))

            // 1. Seed an active UNCERTAIN PUBLISH intent -> key #1
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val (pubReq1, pubCap1) = signedOp(OperationType.PUBLISH, head = HEAD_SHA_1)
            val pub1Outcome = repo.publish(pubReq1, pubCap1)
            assertTrue(pub1Outcome is OperationOutcome.Timeout)
            val keyPublishHead1 = (pub1Outcome as OperationOutcome.Timeout).idempotencyKey
            assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(keyPublishHead1)?.state)

            // 2. Different operation (RECONCILE) on identical task/branch/head -> must get new key #2
            server.enqueue(successAckResponse(op = "RECONCILE", headSha = HEAD_SHA_1, outcome = "MERGED:pr-1"))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val (recReq, recCap) = signedOp(OperationType.RECONCILE, head = HEAD_SHA_1)
            assertTrue(repo.reconcile(recReq, "sub-1", recCap) is OperationOutcome.Succeeded)

            // 3. Same operation (PUBLISH) on identical task/branch with moved headSha -> must get new key #3
            server.enqueue(successAckResponse(op = "PUBLISH", headSha = HEAD_SHA_2))
            server.enqueue(branchHeadResponse(HEAD_SHA_2))
            val (pubReq2, pubCap2) = signedOp(OperationType.PUBLISH, head = HEAD_SHA_2)
            assertTrue(repo.publish(pubReq2, pubCap2) is OperationOutcome.Succeeded)

            // 4. Two UPLOADs with same scope/submissionId/payload differing only in zipSha256 -> distinct keys #4, #5
            val b64Placeholder = Base64.getEncoder().encodeToString("zip-bytes".toByteArray())
            server.enqueue(successAckResponse(op = "UPLOAD", headSha = HEAD_SHA_1))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val (upReq1, upCap1) = signedOp(OperationType.UPLOAD, head = HEAD_SHA_1)
            assertTrue(repo.upload(upReq1, "sub-upload", ZIP_SHA_1, b64Placeholder, upCap1) is OperationOutcome.Succeeded)

            server.enqueue(successAckResponse(op = "UPLOAD", headSha = HEAD_SHA_1))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val (upReq2, upCap2) = signedOp(OperationType.UPLOAD, head = HEAD_SHA_1)
            assertTrue(repo.upload(upReq2, "sub-upload", ZIP_SHA_2, b64Placeholder, upCap2) is OperationOutcome.Succeeded)

            val entries = dao.getAll()
            assertEquals(5, entries.size)
            assertEquals(5, entries.map { it.idempotencyKey }.toSet().size)
            assertEquals(5, entries.map { it.intentFingerprint }.toSet().size)

            val recordedPosts = takeAllRequests()
                .filter { it.method == "POST" }
                .map { bodyJson(it).getValue("idempotencyKey").jsonPrimitive.content }
            assertEquals(5, recordedPosts.size)
            assertEquals(5, recordedPosts.toSet().size)
        }

    @Test
    fun `bullet 1 unresolved ACKNOWLEDGED intent reuses existing key via lookup and terminal conflict issues fresh key on retry`() =
        runTest {
            val dao = InMemoryOperationJournalDao()
            val cipher = RecordingFakeJournalCipher()
            val repo = newRepository(dao = dao, cipher = cipher, keyGenerator = incrementingKeyGenerator(start = 10))
            val intent = samplePublishIntent()

            // Seed an unresolved row already transitioned to ACKNOWLEDGED.
            val existingKey = "%032x".format(99)
            val entity = OperationJournalCodec.encryptNewEntity(
                idempotencyKey = existingKey,
                intent = intent,
                cipher = cipher,
                nowEpochMs = clock.now().toEpochMilli(),
            )
            dao.commitIntentIfNoActive(entity, OperationRepositoryImpl.IN_FLIGHT_STATES)
            assertTrue(dao.transitionState(existingKey, OperationJournalState.INTENT_RECORDED, OperationJournalState.SENT, 1L))
            assertTrue(dao.transitionState(existingKey, OperationJournalState.SENT, OperationJournalState.ACKNOWLEDGED, 2L))

            // Repeat tap while ACKNOWLEDGED must look up existingKey and never send a write.
            server.enqueue(completedLookupResponse(existingKey))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            var writeSendCount = 0
            val resolved = repo.executeOperation(intent) {
                writeSendCount++
                GatewayAckDto(accepted = true)
            }
            assertEquals(0, writeSendCount)
            assertEquals(OperationOutcome.Succeeded(auditRef = "audit-lookup-ok", headSha = HEAD_SHA_1), resolved)
            assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(existingKey)?.state)
            assertEquals(
                listOf("GET /operations/$existingKey", "GET /branches/$BRANCH/head"),
                takeAllRequests().methodPaths(),
            )

            // Contrast: once an operation reaches a non-succeeded terminal state (TERMINAL_CONFLICT),
            // a deliberate new attempt for the same intent generates a fresh key rather than reusing it.
            val conflictIntent = samplePublishIntent(task = "10077")
            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setBody("""{"code": "HEAD_DRIFT", "message": "head drifted"}"""),
            )
            val (req1, cap1) = signedOp(OperationType.PUBLISH, task = "10077", head = HEAD_SHA_1)
            val firstOutcome = repo.publish(req1, cap1)
            assertEquals(OperationOutcome.Conflict(code = "HEAD_DRIFT"), firstOutcome)
            val firstKey = dao.getAll().last().idempotencyKey

            server.enqueue(successAckResponse(op = "PUBLISH", headSha = HEAD_SHA_1))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val (req2, cap2) = signedOp(OperationType.PUBLISH, task = "10077", head = HEAD_SHA_1)
            val secondOutcome = repo.publish(req2, cap2)
            assertEquals(OperationOutcome.Succeeded(auditRef = "audit-ok", headSha = HEAD_SHA_1), secondOutcome)
            val secondKey = dao.getAll().last().idempotencyKey
            assertNotEquals(firstKey, secondKey)
            assertEquals(2, dao.getAll().count { it.intentFingerprint == conflictIntent.fingerprint() })
        }

    /**
     * Verifies Finding F1 fix:
     * `OperationRepositoryImpl.decide` includes normalized `notes` in [OperationIntent] so two
     * `DECIDE` intents with identical target/decision and differing `notes` get distinct
     * `intentFingerprint`s and distinct keys even while the first intent is unresolved, whereas
     * whitespace-trimmed equivalents reuse the unresolved key.
     */
    @Test
    fun `bullet 2 DECIDE includes notes in OperationIntent so differing notes get distinct keys`() =
        runTest {
            val dao = InMemoryOperationJournalDao()
            val repo = newRepository(dao = dao, keyGenerator = incrementingKeyGenerator(start = 20))

            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val (req1, cap1) = signedOp(OperationType.DECIDE, head = HEAD_SHA_1)
            val first = repo.decide(req1, decision = "APPROVED", notes = "Rationale A", capability = cap1)
            assertTrue(first is OperationOutcome.Timeout)
            val key1 = (first as OperationOutcome.Timeout).idempotencyKey

            // Equivalent trimmed notes reuse key1 via lookup without sending a second POST.
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"id":"$key1","operation":"DECIDE","status":"pending","timestamp":1761840050}""",
                ),
            )
            val (req1Repeat, cap1Repeat) = signedOp(OperationType.DECIDE, head = HEAD_SHA_1)
            val repeated = repo.decide(req1Repeat, decision = "APPROVED", notes = "  Rationale A  ", capability = cap1Repeat)
            assertEquals(OperationOutcome.UncertainCompletion(idempotencyKey = key1), repeated)
            assertEquals(1, dao.getAll().size)

            // Different notes on an otherwise identical DECIDE while key1 is UNCERTAIN dispatches with a distinct key2.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val (req2, cap2) = signedOp(OperationType.DECIDE, head = HEAD_SHA_1)
            val second = repo.decide(
                req2,
                decision = "APPROVED",
                notes = "Completely different rationale B",
                capability = cap2,
            )
            assertTrue(second is OperationOutcome.Timeout)
            val key2 = (second as OperationOutcome.Timeout).idempotencyKey
            assertNotEquals(key1, key2)
            assertEquals(2, dao.getAll().size)
            assertNotEquals(dao.findByKey(key1)?.intentFingerprint, dao.findByKey(key2)?.intentFingerprint)
            assertEquals(
                listOf("POST /plans/atlas/decide", "GET /operations/$key1", "POST /plans/atlas/decide"),
                takeAllRequests().methodPaths(),
            )
        }

    // -- Bullet 3: Key reuse with different fingerprint -> conflict, zero new writes --

    @Test
    fun `bullet 3 gateway 409 IDEMPOTENCY_KEY_REUSED on write maps to Conflict TERMINAL_CONFLICT with zero retries`() =
        runTest {
            val dao = InMemoryOperationJournalDao()
            val cipher = RecordingFakeJournalCipher()
            val repo = newRepository(dao = dao, cipher = cipher, keyGenerator = incrementingKeyGenerator(start = 30))
            val (request, signed) = signedOp(OperationType.PUBLISH, head = HEAD_SHA_1)

            server.enqueue(
                MockResponse()
                    .setResponseCode(409)
                    .setBody(
                        """{"code":"IDEMPOTENCY_KEY_REUSED","message":"The key is already recorded with a different request fingerprint.","request_id":"req-30"}""",
                    ),
            )

            val outcome = repo.publish(request, signed)

            assertEquals(OperationOutcome.Conflict(code = "IDEMPOTENCY_KEY_REUSED"), outcome)
            val entry = dao.getAll().single()
            assertEquals(OperationJournalState.TERMINAL_CONFLICT, entry.state)
            val decrypted = OperationJournalCodec.decryptRecord(entry, cipher)
            assertEquals("IDEMPOTENCY_KEY_REUSED", decrypted.lastErrorCode)

            val recorded = takeAllRequests()
            assertEquals(listOf("POST /tasks/10008/publish"), recorded.methodPaths())

            // Recovery sweep never retries or modifies a TERMINAL_CONFLICT record.
            val recovery = repo.recoverUncertainOperations()
            assertEquals(0, recovery.resolvedCount)
            assertEquals(0, recovery.retryCount)
            assertTrue(takeAllRequests().isEmpty())
        }

    @Test
    fun `bullet 3 lookup refused with IDEMPOTENCY_KEY_REUSED maps to Conflict and remains monotonic`() = runTest {
        val dao = InMemoryOperationJournalDao()
        val repo = newRepository(dao = dao, keyGenerator = incrementingKeyGenerator(start = 40))
        val (request, signed) = signedOp(OperationType.PUBLISH, head = HEAD_SHA_1)

        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val timeoutOutcome = repo.publish(request, signed)
        assertTrue(timeoutOutcome is OperationOutcome.Timeout)
        val key = (timeoutOutcome as OperationOutcome.Timeout).idempotencyKey

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "$key",
                  "operation": "PUBLISH",
                  "status": "refused",
                  "code": "IDEMPOTENCY_KEY_REUSED",
                  "auditRef": "audit-key-reused-1",
                  "timestamp": 1761840060
                }
                """.trimIndent(),
            ),
        )
        val summary = repo.recoverUncertainOperations()
        assertEquals(1, summary.resolvedCount)
        assertEquals(0, summary.retryCount)
        assertEquals(
            OperationOutcome.Conflict(code = "IDEMPOTENCY_KEY_REUSED", auditRef = "audit-key-reused-1"),
            summary.outcomes[key],
        )
        assertEquals(OperationJournalState.TERMINAL_CONFLICT, dao.findByKey(key)?.state)

        // Monotonicity: a subsequent lookup returning completed must never overwrite TERMINAL_CONFLICT.
        server.enqueue(completedLookupResponse(key))
        server.enqueue(branchHeadResponse(HEAD_SHA_1))
        val replayLookup = repo.lookupOperation(key)
        assertEquals(
            OperationOutcome.Conflict(code = "IDEMPOTENCY_KEY_REUSED", auditRef = "audit-key-reused-1"),
            replayLookup,
        )
        assertEquals(OperationJournalState.TERMINAL_CONFLICT, dao.findByKey(key)?.state)

        // Only the original POST + first GET /operations/{key} reached the wire.
        assertEquals(
            listOf("POST /tasks/10008/publish", "GET /operations/$key"),
            takeAllRequests().methodPaths(),
        )
    }

    /**
     * Verifies Finding F2 fix:
     * When [IdempotencyKeyGenerator] produces a key that already exists in `operation_journal`
     * under a different [OperationIntent.fingerprint], the `@PrimaryKey` `ABORT` constraint prevents
     * any write dispatch (`0` network sends), leaves the original record intact, and
     * [OperationRepositoryImpl] returns [OperationOutcome.Conflict] (`IDEMPOTENCY_KEY_REUSED`).
     */
    @Test
    fun `bullet 3 local key reuse with different fingerprint returns Conflict, sends nothing, and preserves existing row`() =
        runTest {
            val dao = InMemoryOperationJournalDao()
            // Repeating byte 0x5A yields key = "5a".repeat(16) on every generate() call.
            val repeatingKeyGen = IdempotencyKeyGenerator(SecureRandomSource(ConstantSecureRandom(0x5A)))
            val repo = newRepository(dao = dao, keyGenerator = repeatingKeyGen)

            val (req1, cap1) = signedOp(OperationType.PUBLISH, task = "10008", head = HEAD_SHA_1)
            server.enqueue(successAckResponse(op = "PUBLISH", headSha = HEAD_SHA_1))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val firstOutcome = repo.publish(req1, cap1)
            assertEquals(OperationOutcome.Succeeded(auditRef = "audit-ok", headSha = HEAD_SHA_1), firstOutcome)
            val originalEntry = dao.getAll().single()
            val expectedKey = "5a".repeat(16)
            assertEquals(expectedKey, originalEntry.idempotencyKey)
            assertEquals(2, takeAllRequests().size)

            // Second, materially different intent tries to reuse expectedKey -> blocked as Conflict before wire dispatch.
            val (req2, cap2) = signedOp(OperationType.PUBLISH, task = "10099", head = HEAD_SHA_1)
            val secondOutcome = repo.publish(req2, cap2)

            assertEquals(
                OperationOutcome.Conflict(code = OperationRepositoryImpl.CODE_IDEMPOTENCY_KEY_REUSED),
                secondOutcome,
            )
            assertTrue("No network request may be dispatched on key/fingerprint collision", takeAllRequests().isEmpty())
            assertEquals(1, dao.getAll().size)
            assertEquals(originalEntry, dao.findByKey(expectedKey))
        }

    /**
     * Verifies Finding F4 fix:
     * Before dispatching a recorded entry or recovering/looking up an existing journal row,
     * [OperationRepositoryImpl] verifies that `decrypted.intent.fingerprint()` matches the stored
     * `intentFingerprint`. Any mismatch fails closed to [OperationOutcome.RecoveryRequired] and
     * performs zero network calls.
     */
    @Test
    fun `stored intentFingerprint mismatch fails closed to RecoveryRequired before dispatch and recovery`() =
        runTest {
            // 1. Pre-dispatch mismatch on INTENT_RECORDED row.
            run {
                val dao = InMemoryOperationJournalDao()
                val cipher = RecordingFakeJournalCipher()
                val intent = samplePublishIntent(task = "10008")
                val key = "cc".repeat(16)
                val valid = OperationJournalCodec.encryptNewEntity(
                    idempotencyKey = key,
                    intent = intent,
                    cipher = cipher,
                    nowEpochMs = clock.now().toEpochMilli(),
                )
                val tamperedFingerprint = samplePublishIntent(task = "99999").fingerprint()
                val tamperedEntity = valid.copy(intentFingerprint = tamperedFingerprint)
                dao.commitIntentIfNoActive(tamperedEntity, OperationRepositoryImpl.IN_FLIGHT_STATES)

                val repo = newRepository(dao = dao, cipher = cipher)
                var sendCount = 0
                val outcome = repo.executeOperation(samplePublishIntent(task = "99999")) {
                    sendCount++
                    GatewayAckDto(accepted = true, headSha = HEAD_SHA_1, auditRef = "should-not-send")
                }

                assertEquals(OperationOutcome.RecoveryRequired(idempotencyKey = key), outcome)
                assertEquals(0, sendCount)
                assertTrue(takeAllRequests().isEmpty())
                assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)
            }

            // 2. Pre-recovery mismatch on UNCERTAIN row blocks both recoverUncertainOperations() and lookupOperation().
            run {
                val dao = InMemoryOperationJournalDao()
                val cipher = RecordingFakeJournalCipher()
                val intent = samplePublishIntent(task = "10008")
                val key = "dd".repeat(16)
                val valid = OperationJournalCodec.encryptNewEntity(
                    idempotencyKey = key,
                    intent = intent,
                    cipher = cipher,
                    nowEpochMs = clock.now().toEpochMilli(),
                )
                val tamperedEntity = valid.copy(intentFingerprint = samplePublishIntent(task = "88888").fingerprint())
                dao.commitIntentIfNoActive(tamperedEntity, OperationRepositoryImpl.IN_FLIGHT_STATES)
                assertTrue(dao.transitionState(key, OperationJournalState.INTENT_RECORDED, OperationJournalState.SENT, 1L))
                assertTrue(dao.transitionState(key, OperationJournalState.SENT, OperationJournalState.UNCERTAIN, 2L))

                val repo = newRepository(dao = dao, cipher = cipher)
                val lookupOutcome = repo.lookupOperation(key)
                assertEquals(OperationOutcome.RecoveryRequired(idempotencyKey = key), lookupOutcome)
                assertTrue(takeAllRequests().isEmpty())
                assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)

                // Now check recoverUncertainOperations() when row is directly in SENT/UNCERTAIN.
                val dao2 = InMemoryOperationJournalDao()
                val key2 = "ee".repeat(16)
                val valid2 = OperationJournalCodec.encryptNewEntity(
                    idempotencyKey = key2,
                    intent = intent,
                    cipher = cipher,
                    nowEpochMs = clock.now().toEpochMilli(),
                )
                val tampered2 = valid2.copy(intentFingerprint = "0".repeat(64))
                dao2.commitIntentIfNoActive(tampered2, OperationRepositoryImpl.IN_FLIGHT_STATES)
                assertTrue(dao2.transitionState(key2, OperationJournalState.INTENT_RECORDED, OperationJournalState.SENT, 1L))

                val repo2 = newRepository(dao = dao2, cipher = cipher)
                val summary = repo2.recoverUncertainOperations()
                assertEquals(1, summary.resolvedCount)
                assertEquals(0, summary.retryCount)
                assertEquals(OperationOutcome.RecoveryRequired(idempotencyKey = key2), summary.outcomes[key2])
                assertTrue(takeAllRequests().isEmpty())
                assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao2.findByKey(key2)?.state)
            }
        }

    // -- Bullet 4: Process restart never generates a new key for unresolved intent --

    @Test
    fun `bullet 4 process restart across every unresolved state keeps original key and never creates a new key`() =
        runTest {
            // Case A: Restart while INTENT_RECORDED (crash before send) with a differently-seeded keyGenerator.
            run {
                val dao = InMemoryOperationJournalDao()
                val cipher = RecordingFakeJournalCipher()
                val intent = samplePublishIntent(task = "10008")
                val originalKey = "11".repeat(16)
                val recorded = OperationJournalCodec.encryptNewEntity(
                    idempotencyKey = originalKey,
                    intent = intent,
                    cipher = cipher,
                    nowEpochMs = 1_000L,
                )
                dao.commitIntentIfNoActive(recorded, OperationRepositoryImpl.IN_FLIGHT_STATES)

                // Simulated restarted process uses a generator that would emit "99".repeat(16) if ever invoked.
                val restartedRepo = newRepository(
                    dao = dao,
                    cipher = cipher,
                    keyGenerator = IdempotencyKeyGenerator(SecureRandomSource(ConstantSecureRandom(0x99))),
                )
                server.enqueue(successAckResponse(op = "PUBLISH", headSha = HEAD_SHA_1))
                server.enqueue(branchHeadResponse(HEAD_SHA_1))
                val (reqAfterRestart, capAfterRestart) = signedOp(OperationType.PUBLISH, task = "10008", head = HEAD_SHA_1)
                val outcome = restartedRepo.publish(reqAfterRestart, capAfterRestart)

                assertEquals(OperationOutcome.Succeeded(auditRef = "audit-ok", headSha = HEAD_SHA_1), outcome)
                assertEquals(1, dao.getAll().size)
                val postReq = takeAllRequests().first { it.method == "POST" }
                assertEquals(originalKey, bodyJson(postReq).getValue("idempotencyKey").jsonPrimitive.content)
            }

            // Case B: Restarts while SENT, ACKNOWLEDGED, and UNCERTAIN (re-tap + repeated pending recovery).
            for ((idx, unresolvedState) in listOf(
                OperationJournalState.SENT,
                OperationJournalState.ACKNOWLEDGED,
                OperationJournalState.UNCERTAIN,
            ).withIndex()) {
                val dao = InMemoryOperationJournalDao()
                val cipher = RecordingFakeJournalCipher()
                val task = "2000$idx"
                val intent = samplePublishIntent(task = task)
                val originalKey = "%032x".format(0xA0 + idx)

                val entity = OperationJournalCodec.encryptNewEntity(
                    idempotencyKey = originalKey,
                    intent = intent,
                    cipher = cipher,
                    nowEpochMs = 2_000L,
                )
                dao.commitIntentIfNoActive(entity, OperationRepositoryImpl.IN_FLIGHT_STATES)
                dao.transitionState(originalKey, OperationJournalState.INTENT_RECORDED, OperationJournalState.SENT, 2_100L)
                if (unresolvedState == OperationJournalState.ACKNOWLEDGED || unresolvedState == OperationJournalState.UNCERTAIN) {
                    dao.transitionState(originalKey, OperationJournalState.SENT, unresolvedState, 2_200L)
                }

                // Restart #1: user re-taps the same intent while still unresolved; lookup returns pending.
                val process2 = newRepository(
                    dao = dao,
                    cipher = cipher,
                    keyGenerator = IdempotencyKeyGenerator(SecureRandomSource(ConstantSecureRandom(0xB0 + idx))),
                )
                server.enqueue(
                    MockResponse().setResponseCode(200).setBody(
                        """{"id":"$originalKey","operation":"PUBLISH","status":"pending","timestamp":1761840010}""",
                    ),
                )
                val (req2, cap2) = signedOp(OperationType.PUBLISH, task = task, head = HEAD_SHA_1)
                val tapAfterRestart1 = process2.publish(req2, cap2)
                assertEquals(OperationOutcome.UncertainCompletion(idempotencyKey = originalKey), tapAfterRestart1)
                assertEquals(1, dao.getAll().size)
                assertEquals(originalKey, dao.getAll().single().idempotencyKey)
                assertEquals(listOf("GET /operations/$originalKey"), takeAllRequests().methodPaths())

                // Restart #2: recovery worker runs after another process restart with yet another keyGenerator.
                val process3 = newRepository(
                    dao = dao,
                    cipher = cipher,
                    keyGenerator = IdempotencyKeyGenerator(SecureRandomSource(ConstantSecureRandom(0xC0 + idx))),
                )
                server.enqueue(completedLookupResponse(originalKey))
                server.enqueue(branchHeadResponse(HEAD_SHA_1))
                val recovery = process3.recoverUncertainOperations()

                assertEquals(1, recovery.resolvedCount)
                assertEquals(OperationOutcome.Succeeded(auditRef = "audit-lookup-ok", headSha = HEAD_SHA_1), recovery.outcomes[originalKey])
                assertEquals(1, dao.getAll().size)
                assertEquals(originalKey, dao.getAll().single().idempotencyKey)
                assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(originalKey)?.state)
                assertEquals(
                    listOf("GET /operations/$originalKey", "GET /branches/$BRANCH/head"),
                    takeAllRequests().methodPaths(),
                )
            }
        }

    // -- Bullet 5: Idempotency keys are never confused with capability nonces --

    @Test
    fun `bullet 5 idempotency key and capability nonce remain distinct on shared CSPRNG, wire, re-mint, and lookup`() =
        runTest {
            val sharedRandom = SecureRandomSource(Random(20261116L))
            val nonceSource = DeviceNonceSource(sharedRandom)
            val sharedKeyGenerator = IdempotencyKeyGenerator(sharedRandom)
            val minter = CapabilityMinter(
                signer = HmacCapabilitySigner(keyId = "k2", secretUtf8 = "test-secret-key-32-bytes-long!!"),
                nonceSource = nonceSource,
                clock = clock,
            )

            val dao = InMemoryOperationJournalDao()
            val cipher = RecordingFakeJournalCipher()
            val repo = newRepository(dao = dao, cipher = cipher, keyGenerator = sharedKeyGenerator)
            val capturedLogs = mutableListOf<String>()
            Logger.logSink = { _, tag, message, error ->
                capturedLogs.add("$tag: $message ${error?.message.orEmpty()}")
            }

            // 1. Pre-record INTENT_RECORDED using key1, then mint capability #1 (nonce1).
            val intent = samplePublishIntent(task = "10008")
            val nonce1 = nonceSource.newNonce()
            val scope1 = CapabilityScope(OperationType.PUBLISH, BRANCH, HEAD_SHA_1, nonce1)
            val cap1 = minter.mint(scope = scope1, order = "order-7731", plan = "atlas", task = "10008")
            val req1 = OperationRequest(OperationType.PUBLISH, scope1, plan = "atlas", task = "10008", order = "order-7731")

            // Simulate a crash right after journal INTENT_RECORDED commit (before send) during Tap 1,
            // then Tap 2 re-mints a brand new capability (nonce2 != nonce1) and encounters lost ack -> lookup.
            val key1 = sharedKeyGenerator.generate()
            assertNotEquals(nonce1, key1)
            dao.commitIntentIfNoActive(
                OperationJournalCodec.encryptNewEntity(
                    idempotencyKey = key1,
                    intent = intent,
                    cipher = cipher,
                    nowEpochMs = clock.now().toEpochMilli(),
                ),
                OperationRepositoryImpl.IN_FLIGHT_STATES,
            )

            val nonce2 = nonceSource.newNonce()
            assertNotEquals(nonce1, nonce2)
            assertNotEquals(nonce2, key1)
            val scope2 = CapabilityScope(OperationType.PUBLISH, BRANCH, HEAD_SHA_1, nonce2)
            val cap2 = minter.mint(scope = scope2, order = "order-7731", plan = "atlas", task = "10008")
            val req2 = OperationRequest(OperationType.PUBLISH, scope2, plan = "atlas", task = "10008", order = "order-7731")

            // Wire POST disconnects after request -> moves entry to UNCERTAIN.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val sendOutcome = repo.publish(req2, cap2)
            assertTrue(sendOutcome is OperationOutcome.Timeout)
            assertEquals(key1, (sendOutcome as OperationOutcome.Timeout).idempotencyKey)

            val postReq = takeAllRequests().single()
            assertEquals("POST", postReq.method)
            val postJson = bodyJson(postReq)
            val wireKey = postJson.getValue("idempotencyKey").jsonPrimitive.content
            val wireCapabilityRaw = postJson.getValue("capability").jsonPrimitive.content
            assertEquals(11, wireCapabilityRaw.split('\n').size)
            val decodedWireCap = OperatorCapabilityCodec.decode(wireCapabilityRaw.substringBeforeLast('\n'))

            assertEquals("Wire POST must keep original journal idempotencyKey across re-mint", key1, wireKey)
            assertEquals("Wire POST capability must carry fresh minted nonce2", nonce2, decodedWireCap.nonce)
            assertNotEquals("Wire idempotencyKey must never equal capability nonce", decodedWireCap.nonce, wireKey)

            // Recovery lookup uses idempotencyKey in GET /operations/{id}, NEVER capability nonce.
            server.enqueue(completedLookupResponse(key1))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val recovery = repo.recoverUncertainOperations()
            assertEquals(OperationOutcome.Succeeded(auditRef = "audit-lookup-ok", headSha = HEAD_SHA_1), recovery.outcomes[key1])

            val recoveryPaths = takeAllRequests().methodPaths()
            assertEquals(listOf("GET /operations/$key1", "GET /branches/$BRANCH/head"), recoveryPaths)
            assertFalse(recoveryPaths.any { it.contains(nonce1) || it.contains(nonce2) })

            // Journal entity, cipher plaintexts, and logs retain key1 only and never contain any nonce.
            val storedText = dao.getAll().single().toString()
            val plaintexts = cipher.recordedPlaintexts.joinToString("\n")
            val logs = capturedLogs.joinToString("\n")
            assertEquals(key1, dao.getAll().single().idempotencyKey)
            for (forbiddenNonce in listOf(nonce1, nonce2, "ctxop-v1", cap1.signatureBase64Url, cap2.signatureBase64Url)) {
                assertFalse("Entity must not contain $forbiddenNonce", storedText.contains(forbiddenNonce))
                assertFalse("Cipher plaintexts must not contain $forbiddenNonce", plaintexts.contains(forbiddenNonce))
                assertFalse("Logs must not contain $forbiddenNonce", logs.contains(forbiddenNonce))
            }
        }

    // -- Helpers ---------------------------------------------------------------

    private fun newRepository(
        dao: InMemoryOperationJournalDao = InMemoryOperationJournalDao(),
        cipher: RecordingFakeJournalCipher = RecordingFakeJournalCipher(),
        keyGenerator: IdempotencyKeyGenerator = IdempotencyKeyGenerator(SecureRandomSource()),
    ): OperationRepositoryImpl = OperationRepositoryImpl(
        gateway = gatewayRepository,
        journalDao = dao,
        cipher = cipher,
        keyGenerator = keyGenerator,
        clock = clock,
    )

    private fun incrementingKeyGenerator(start: Int): IdempotencyKeyGenerator =
        IdempotencyKeyGenerator(SecureRandomSource(IncrementingByteSecureRandom(start)))

    private fun samplePublishIntent(
        task: String = "10008",
        head: String = HEAD_SHA_1,
    ): OperationIntent = OperationIntent(
        operation = OperationType.PUBLISH,
        plan = "atlas",
        task = task,
        branch = BRANCH,
        expectedHead = head,
    )

    private fun signedOp(
        op: OperationType,
        task: String = "10008",
        head: String = HEAD_SHA_1,
    ): Pair<OperationRequest, SignedCapability> {
        val nonceSource = DeviceNonceSource(SecureRandomSource())
        val minter = CapabilityMinter(
            signer = HmacCapabilitySigner(keyId = "k2", secretUtf8 = "test-secret-key-32-bytes-long!!"),
            nonceSource = nonceSource,
            clock = clock,
        )
        val scope = CapabilityScope(op = op, branch = BRANCH, head = head, nonce = nonceSource.newNonce())
        val req = OperationRequest(op = op, scope = scope, plan = "atlas", task = task, order = "order-7731")
        val cap = minter.mint(scope = scope, order = "order-7731", plan = "atlas", task = task)
        return req to cap
    }

    private fun successAckResponse(
        op: String,
        headSha: String,
        outcome: String = "",
    ): MockResponse {
        val outcomeField = if (outcome.isNotEmpty()) ""","outcome":"$outcome"""" else ""
        return MockResponse().setResponseCode(200).setBody(
            """
            {
              "accepted": true,
              "operation": "$op",
              "branch": "$BRANCH",
              "headSha": "$headSha",
              "auditRef": "audit-ok"$outcomeField
            }
            """.trimIndent(),
        )
    }

    private fun completedLookupResponse(key: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(
            """
            {
              "id": "$key",
              "operation": "PUBLISH",
              "status": "completed",
              "branch": "$BRANCH",
              "headSha": "$HEAD_SHA_1",
              "auditRef": "audit-lookup-ok",
              "timestamp": 1761840050
            }
            """.trimIndent(),
        )

    private fun branchHeadResponse(headSha: String): MockResponse =
        MockResponse().setResponseCode(200).setBody("""{"branch":"$BRANCH","headSha":"$headSha"}""")

    private fun takeAllRequests(): List<RecordedRequest> {
        val out = mutableListOf<RecordedRequest>()
        while (true) {
            val req = server.takeRequest(10, TimeUnit.MILLISECONDS) ?: break
            out.add(req)
        }
        return out
    }

    private fun List<RecordedRequest>.methodPaths(): List<String> =
        map { "${it.method} ${it.path}" }

    private fun bodyJson(request: RecordedRequest) =
        Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    private class ConstantSecureRandom(private val byteValue: Int) : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            bytes.fill(byteValue.toByte())
        }
    }

    private class IncrementingByteSecureRandom(start: Int) : SecureRandom() {
        private var counter = start
        override fun nextBytes(bytes: ByteArray) {
            bytes.fill((counter++ and 0xFF).toByte())
        }
    }

    private companion object {
        const val BRANCH = "task-10008-refactor-parser"
        const val HEAD_SHA_1 = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
        const val HEAD_SHA_2 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val ZIP_SHA_1 = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
        const val ZIP_SHA_2 = "1111111111111111111111111111111111111111111111111111111111111111"
        val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}
