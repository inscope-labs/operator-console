package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.capability.CapabilityMinter
import com.inscopelabs.buildengine.capability.DeviceNonceSource
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
import com.inscopelabs.buildengine.data.local.JournalFieldCipher
import com.inscopelabs.buildengine.data.local.JournalGcm
import com.inscopelabs.buildengine.data.local.OperationIntent
import com.inscopelabs.buildengine.data.local.OperationJournalDao
import com.inscopelabs.buildengine.data.local.OperationJournalEntity
import com.inscopelabs.buildengine.data.local.OperationJournalState
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.OperationOutcome
import com.inscopelabs.buildengine.domain.model.OperationRequest
import com.inscopelabs.buildengine.testutil.InMemoryOperationJournalDao
import com.inscopelabs.buildengine.testutil.RecordingFakeJournalCipher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic JVM fault-injection tests closing the gaps in the PR3-015 14-scenario fault matrix
 * (T3-205):
 * - Scenario 1: Crash before journal commit (0 rows, 0 sends, clean fresh execution after restart).
 * - Scenario 4: Wire-level network loss before request reaches gateway -> 404 OPERATION_NOT_FOUND
 *   recovery with zero automatic resends.
 * - Scenario 5: Wire-level HTTP 504 Gateway Timeout on write -> UNCERTAIN -> lookup recovery.
 * - Scenario 7: Malformed JSON, truncated JSON, truncated HTTP stream (EOF), empty `{}` ack,
 *   malformed error body, and malformed/truncated lookup response over real Ktor/OkHttp +
 *   [GatewayRepository] decoding.
 * - Scenario 11: Repeated lookup unavailability across consecutive transport/5xx/live-head outages
 *   keeping journal `UNCERTAIN` with zero write resends until eventual recovery.
 * - Scenario 13: Real AES-256-GCM ([JournalGcm]) ciphertext decryption failure (key mismatch / tag
 *   tamper) failing closed on both duplicate tap and direct lookup with zero network traffic.
 */
class FaultMatrixTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var gatewayRepository: GatewayRepository
    private lateinit var clock: FixedClock
    private lateinit var keyGenerator: IdempotencyKeyGenerator

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
        keyGenerator = IdempotencyKeyGenerator(SecureRandomSource())
    }

    @After
    fun tearDown() {
        httpClient.close()
        runCatching { server.shutdown() }
    }

    // -- Scenario 1: Crash before journal commit --------------------------------

    @Test
    fun `scenario 1 crash before journal commit leaves zero rows, sends nothing, and allows clean fresh execution`() =
        runTest {
            val dao = FaultyJournalDao().apply {
                crashBeforeCommit = {
                    throw SimulatedProcessCrash("killed before journal transaction commits")
                }
            }
            val cipher = RecordingFakeJournalCipher()
            val instance1 = newRepository(dao = dao, cipher = cipher)
            val (request, signed) = signedPublish()

            server.enqueue(successAckResponse())
            val crash = runCatching { instance1.publish(request, signed) }.exceptionOrNull()

            assertTrue("Expected simulated pre-commit crash, got $crash", crash is SimulatedProcessCrash)
            assertEquals(0, server.requestCount)
            assertEquals(0, dao.getAll().size)

            // Restart with clean DAO state: fresh execution commits a row and sends exactly once.
            dao.crashBeforeCommit = null
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val instance2 = newRepository(dao = dao, cipher = cipher)
            val outcome = instance2.publish(request, signed)

            assertEquals(OperationOutcome.Succeeded(auditRef = "audit-ok-1", headSha = HEAD_SHA_1), outcome)
            assertEquals(1, dao.getAll().size)
            assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.getAll().single().state)
            val recorded = takeAllRequests()
            assertEquals(listOf("POST /tasks/10008/publish", "GET /branches/$BRANCH/head"), recorded.methodPaths())
        }

    // -- Scenario 4 & 5: Wire-level network loss before gateway & 504 on write --

    @Test
    fun `scenario 4 network loss before request reaches gateway marks UNCERTAIN then 404 lookup marks RECOVERY_REQUIRED with zero resend`() =
        runTest {
            val dao = FaultyJournalDao()
            val unreachablePortServer = MockWebServer().apply {
                start()
                shutdown()
            }
            val unreachableGateway = GatewayRepository(
                httpClient = GatewayHttpClient(
                    client = httpClient,
                    baseUrlProvider = { unreachablePortServer.url("/").toString() },
                ),
                errorMapper = ErrorMapper(),
            )
            val offlineRepo = newRepository(dao = dao, gateway = unreachableGateway)
            val (request, signed) = signedPublish()

            val writeOutcome = offlineRepo.publish(request, signed)

            assertTrue("Expected Timeout on pre-connect IO failure, got $writeOutcome", writeOutcome is OperationOutcome.Timeout)
            val key = (writeOutcome as OperationOutcome.Timeout).idempotencyKey
            assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)

            // Once connectivity returns, recovery uses GET /operations/{id} only -> 404 OPERATION_NOT_FOUND.
            server.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setBody("""{"code": "OPERATION_NOT_FOUND", "message": "not found"}"""),
            )
            val recoveryRepo = newRepository(dao = dao, gateway = gatewayRepository)
            val summary = recoveryRepo.recoverUncertainOperations()

            assertEquals(1, summary.resolvedCount)
            assertEquals(0, summary.retryCount)
            assertEquals(
                OperationOutcome.RecoveryRequired(idempotencyKey = key, code = "OPERATION_NOT_FOUND"),
                summary.outcomes[key],
            )
            assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)
            assertEquals(listOf("GET /operations/$key"), takeAllRequests().methodPaths())
        }

    @Test
    fun `scenario 5 gateway 504 timeout on write marks UNCERTAIN and lookup resolves without resend`() = runTest {
        val dao = FaultyJournalDao()
        val repo = newRepository(dao = dao)
        val (request, signed) = signedPublish()

        server.enqueue(MockResponse().setResponseCode(504).setBody("""{"code": "GATEWAY_TIMEOUT"}"""))
        val writeOutcome = repo.publish(request, signed)

        assertTrue(writeOutcome is OperationOutcome.Timeout)
        val key = (writeOutcome as OperationOutcome.Timeout).idempotencyKey
        assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)

        server.enqueue(completedLookupResponse(key))
        server.enqueue(branchHeadResponse(HEAD_SHA_1))
        val summary = repo.recoverUncertainOperations()

        assertEquals(1, summary.resolvedCount)
        assertEquals(0, summary.retryCount)
        assertEquals(OperationOutcome.Succeeded(auditRef = "audit-lookup-ok", headSha = HEAD_SHA_1), summary.outcomes[key])
        assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(key)?.state)
        assertEquals(
            listOf(
                "POST /tasks/10008/publish",
                "GET /operations/$key",
                "GET /branches/$BRANCH/head",
            ),
            takeAllRequests().methodPaths(),
        )
    }

    // -- Scenario 7: Malformed or truncated acknowledgement (wire -> executor) --

    @Test
    fun `scenario 7 malformed JSON write acknowledgement marks UNCERTAIN and lookup resolves without resend`() =
        runTest {
            val dao = FaultyJournalDao()
            val repo = newRepository(dao = dao)
            val (request, signed) = signedPublish()

            // Gateway returns 200 OK with non-JSON / malformed payload.
            server.enqueue(MockResponse().setResponseCode(200).setBody("<html>unexpected proxy body</html>"))
            val outcome = repo.publish(request, signed)

            assertTrue("Malformed 200 ack must be UncertainCompletion, got $outcome", outcome is OperationOutcome.UncertainCompletion)
            val key = (outcome as OperationOutcome.UncertainCompletion).idempotencyKey
            assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)

            // Recovery resolves via lookup only and never resends the write.
            server.enqueue(completedLookupResponse(key))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val summary = repo.recoverUncertainOperations()

            assertEquals(1, summary.resolvedCount)
            assertEquals(0, summary.retryCount)
            assertEquals(OperationOutcome.Succeeded(auditRef = "audit-lookup-ok", headSha = HEAD_SHA_1), summary.outcomes[key])
            assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(key)?.state)
            assertEquals(
                listOf(
                    "POST /tasks/10008/publish",
                    "GET /operations/$key",
                    "GET /branches/$BRANCH/head",
                ),
                takeAllRequests().methodPaths(),
            )
        }

    @Test
    fun `scenario 7 truncated JSON, truncated HTTP stream, empty ack object, and malformed error body never succeed or resend`() =
        runTest {
            // Case A: 200 OK with truncated JSON string
            run {
                val dao = FaultyJournalDao()
                val repo = newRepository(dao = dao)
                val (request, signed) = signedPublish(task = "10008")
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"accepted":true,"operation":"PUBLISH","auditRef":"aud"""),
                )

                val outcome = repo.publish(request, signed)
                assertTrue("Truncated JSON ack must be UncertainCompletion, got $outcome", outcome is OperationOutcome.UncertainCompletion)
                assertEquals(OperationJournalState.UNCERTAIN, dao.getAll().single().state)
                assertEquals(listOf("POST /tasks/10008/publish"), takeAllRequests().methodPaths())
            }

            // Case B: 200 OK with chunked HTTP body disconnected mid-stream (EOF / IOException)
            run {
                val dao = FaultyJournalDao()
                val repo = newRepository(dao = dao)
                val (request, signed) = signedPublish(task = "10009")
                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setChunkedBody("""{"accepted":true,"operation":"PUBLISH","headSha":"$HEAD_SHA_1"}""", 8)
                        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
                )

                val outcome = repo.publish(request, signed)
                assertTrue("Truncated HTTP stream must fail closed to Timeout/Uncertain, got $outcome", outcome is OperationOutcome.Timeout)
                assertEquals(OperationJournalState.UNCERTAIN, dao.getAll().single().state)
                assertEquals(listOf("POST /tasks/10009/publish"), takeAllRequests().methodPaths())
            }

            // Case C: 200 OK with empty JSON object `{}` (accepted defaults false)
            run {
                val dao = FaultyJournalDao()
                val repo = newRepository(dao = dao)
                val (request, signed) = signedPublish(task = "10010")
                server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

                val outcome = repo.publish(request, signed)
                assertTrue("Empty `{}` ack must be UncertainCompletion, got $outcome", outcome is OperationOutcome.UncertainCompletion)
                assertEquals(OperationJournalState.UNCERTAIN, dao.getAll().single().state)
                assertEquals(listOf("POST /tasks/10010/publish"), takeAllRequests().methodPaths())
            }

            // Case D: Non-2xx (502) with truncated/non-JSON body -> RecoveryRequired (no code hallucinated)
            run {
                val dao = FaultyJournalDao()
                val repo = newRepository(dao = dao)
                val (request, signed) = signedPublish(task = "10011")
                server.enqueue(MockResponse().setResponseCode(502).setBody("""{"code": "GATE"""))

                val outcome = repo.publish(request, signed)
                assertTrue("Malformed error body must be RecoveryRequired, got $outcome", outcome is OperationOutcome.RecoveryRequired)
                assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.getAll().single().state)
                assertEquals(listOf("POST /tasks/10011/publish"), takeAllRequests().methodPaths())
            }
        }

    @Test
    fun `scenario 7 malformed or truncated lookup response fails closed without mutating to success or resending`() =
        runTest {
            // Case A: Truncated JSON on GET /operations/{id} -> GatewayProtocolException -> RecoveryRequired
            run {
                val dao = FaultyJournalDao()
                val repo = newRepository(dao = dao)
                val key = seedUncertainViaWireTimeout(repo, task = "10008")

                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"id":"$key","operation":"PUBLISH","status":"comp"""),
                )
                val summary = repo.recoverUncertainOperations()

                assertEquals(1, summary.resolvedCount)
                assertEquals(0, summary.retryCount)
                assertEquals(OperationOutcome.RecoveryRequired(idempotencyKey = key), summary.outcomes[key])
                assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(key)?.state)
                assertEquals(
                    listOf("POST /tasks/10008/publish", "GET /operations/$key"),
                    takeAllRequests().methodPaths(),
                )
            }

            // Case B: Disconnected chunked HTTP stream on GET /operations/{id} -> IOException -> TransientRetry (stays UNCERTAIN)
            run {
                val dao = FaultyJournalDao()
                val repo = newRepository(dao = dao)
                val key = seedUncertainViaWireTimeout(repo, task = "10009")

                server.enqueue(
                    MockResponse()
                        .setResponseCode(200)
                        .setChunkedBody("""{"id":"$key","operation":"PUBLISH","status":"completed"}""", 8)
                        .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
                )
                val summary = repo.recoverUncertainOperations()

                assertEquals(0, summary.resolvedCount)
                assertEquals(1, summary.retryCount)
                assertTrue(summary.outcomes[key] is OperationOutcome.UncertainCompletion)
                assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)
                assertEquals(
                    listOf("POST /tasks/10009/publish", "GET /operations/$key"),
                    takeAllRequests().methodPaths(),
                )
            }
        }

    // -- Scenario 11: Lookup is unavailable repeatedly ------------------------

    @Test
    fun `scenario 11 repeated lookup unavailability across network and 5xx outages keeps entry UNCERTAIN and resolves on recovery with zero resend`() =
        runTest {
            val dao = FaultyJournalDao()
            val repo = newRepository(dao = dao)
            val (request, signed) = signedPublish()

            // Initial write disconnects at socket start -> 1 POST sent, journal enters UNCERTAIN.
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val initialOutcome = repo.publish(request, signed)
            assertTrue(initialOutcome is OperationOutcome.Timeout)
            val key = (initialOutcome as OperationOutcome.Timeout).idempotencyKey
            val uncertainSnapshot = dao.findByKey(key)!!
            assertEquals(OperationJournalState.UNCERTAIN, uncertainSnapshot.state)

            // Repeated lookup failures across 6 consecutive recovery attempts:
            // 1) transport disconnect, 2) HTTP 500, 3) HTTP 502, 4) HTTP 503, 5) HTTP 504,
            // 6) lookup 200 completed + live-head 503 transient unavailable.
            val outageResponses = listOf(
                listOf(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)),
                listOf(MockResponse().setResponseCode(500).setBody("""{"code": "INTERNAL_ERROR"}""")),
                listOf(MockResponse().setResponseCode(502).setBody("bad gateway")),
                listOf(MockResponse().setResponseCode(503).setBody("service unavailable")),
                listOf(MockResponse().setResponseCode(504).setBody("gateway timeout")),
                listOf(
                    completedLookupResponse(key),
                    MockResponse().setResponseCode(503).setBody("branch check unavailable"),
                ),
            )

            outageResponses.forEachIndexed { attemptIndex, responses ->
                responses.forEach { server.enqueue(it) }
                clock.advanceSeconds(30L * (attemptIndex + 1))

                val summary = repo.recoverUncertainOperations()

                assertEquals("Attempt $attemptIndex must resolve 0 entries", 0, summary.resolvedCount)
                assertEquals("Attempt $attemptIndex must signal retry", 1, summary.retryCount)
                assertTrue(
                    "Attempt $attemptIndex must stay UncertainCompletion",
                    summary.outcomes[key] is OperationOutcome.UncertainCompletion,
                )
                val current = dao.findByKey(key)!!
                assertEquals(OperationJournalState.UNCERTAIN, current.state)
                assertEquals(uncertainSnapshot.idempotencyKey, current.idempotencyKey)
                assertEquals(uncertainSnapshot.intentFingerprint, current.intentFingerprint)
            }

            // Attempt 7: both lookup and live-head succeed -> TERMINAL_SUCCEEDED, zero second POST write.
            clock.advanceSeconds(60)
            server.enqueue(completedLookupResponse(key))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))

            val finalSummary = repo.recoverUncertainOperations()

            assertEquals(1, finalSummary.resolvedCount)
            assertEquals(0, finalSummary.retryCount)
            assertEquals(
                OperationOutcome.Succeeded(auditRef = "audit-lookup-ok", headSha = HEAD_SHA_1),
                finalSummary.outcomes[key],
            )
            assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(key)?.state)

            val recorded = takeAllRequests()
            val postCount = recorded.count { it.method == "POST" }
            val lookupGetCount = recorded.count { it.method == "GET" && it.path == "/operations/$key" }
            assertEquals("Original write must be dispatched at most once", 1, postCount)
            assertEquals("Expected 7 lookup attempts across 6 outages + 1 recovery", 7, lookupGetCount)
        }

    @Test
    fun `scenario 11 repeated direct lookupOperation calls during 503 and IO outages remain UNCERTAIN with zero resends`() =
        runTest {
            val dao = FaultyJournalDao()
            val repo = newRepository(dao = dao)
            val key = seedUncertainViaWireTimeout(repo, task = "10008")

            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))

            repeat(3) { attempt ->
                clock.advanceSeconds(15)
                val outcome = repo.lookupOperation(key)
                assertTrue(
                    "Direct lookup attempt $attempt must stay UncertainCompletion, got $outcome",
                    outcome is OperationOutcome.UncertainCompletion,
                )
                assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(key)?.state)
            }

            // Fourth direct lookup succeeds cleanly.
            server.enqueue(completedLookupResponse(key))
            server.enqueue(branchHeadResponse(HEAD_SHA_1))
            val resolved = repo.lookupOperation(key)

            assertEquals(OperationOutcome.Succeeded(auditRef = "audit-lookup-ok", headSha = HEAD_SHA_1), resolved)
            assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(key)?.state)
            assertEquals(1, takeAllRequests().count { it.method == "POST" })
        }

    // -- Scenario 13: Real JournalGcm decryption failure on tap & lookup ------

    @Test
    fun `scenario 13 tampered or rotated-key GCM ciphertext fails closed on duplicate tap and direct lookup with zero sends`() =
        runTest {
            val key1 = randomAesKey()
            val key2 = randomAesKey()
            val dao = FaultyJournalDao()
            val repoWithKey1 = newRepository(dao = dao, cipher = RealGcmJournalCipher(key1))

            // Seed an UNCERTAIN entry encrypted under real AES-256-GCM key1.
            val uncertainKey = seedUncertainViaWireTimeout(repoWithKey1, task = "10008")
            assertEquals(OperationJournalState.UNCERTAIN, dao.findByKey(uncertainKey)?.state)
            val originalEntity = dao.findByKey(uncertainKey)!!
            assertTrue(originalEntity.encryptedOperation.startsWith(JournalGcm.FORMAT_PREFIX))
            takeAllRequests() // consume the single initial POST

            // Case A: Key mismatch / lost key on duplicate tap (executeOperation) -> RecoveryRequired, 0 network calls.
            val repoWithWrongKey = newRepository(dao = dao, cipher = RealGcmJournalCipher(key2))
            val (request, signed) = signedPublish(task = "10008")
            val requestsBeforeTap = server.requestCount
            val tapOutcome = repoWithWrongKey.publish(request, signed)

            assertEquals(OperationOutcome.RecoveryRequired(idempotencyKey = uncertainKey), tapOutcome)
            assertEquals(OperationJournalState.RECOVERY_REQUIRED, dao.findByKey(uncertainKey)?.state)
            assertEquals(requestsBeforeTap, server.requestCount)
            assertTrue(takeAllRequests().isEmpty())

            // Case B: Tampered ciphertext on direct lookupOperation -> RecoveryRequired, 0 network calls.
            val daoTampered = FaultyJournalDao()
            val repoForTamper = newRepository(dao = daoTampered, cipher = RealGcmJournalCipher(key1))
            val tamperedKey = seedUncertainViaWireTimeout(repoForTamper, task = "10009")
            takeAllRequests()

            val stored = daoTampered.findByKey(tamperedKey)!!
            daoTampered.overwriteForFaultInjection(
                stored.copy(encryptedOperation = flipLastByte(stored.encryptedOperation)),
            )

            val requestsBeforeLookup = server.requestCount
            val lookupOutcome = repoForTamper.lookupOperation(tamperedKey)
            assertEquals(OperationOutcome.RecoveryRequired(idempotencyKey = tamperedKey), lookupOutcome)
            assertEquals(OperationJournalState.RECOVERY_REQUIRED, daoTampered.findByKey(tamperedKey)?.state)
            assertEquals(requestsBeforeLookup, server.requestCount)
            assertTrue(takeAllRequests().isEmpty())
        }

    // -- Helpers ----------------------------------------------------------------

    private fun newRepository(
        dao: OperationJournalDao,
        cipher: JournalFieldCipher = RecordingFakeJournalCipher(),
        gateway: GatewayRepository = gatewayRepository,
    ): OperationRepositoryImpl = OperationRepositoryImpl(
        gateway = gateway,
        journalDao = dao,
        cipher = cipher,
        keyGenerator = keyGenerator,
        clock = clock,
    )

    private suspend fun seedUncertainViaWireTimeout(
        repo: OperationRepositoryImpl,
        task: String,
    ): String {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val (request, signed) = signedPublish(task = task)
        val outcome = repo.publish(request, signed)
        assertTrue("Expected Timeout while seeding UNCERTAIN row, got $outcome", outcome is OperationOutcome.Timeout)
        return (outcome as OperationOutcome.Timeout).idempotencyKey
    }

    private fun signedPublish(task: String = "10008"): Pair<OperationRequest, SignedCapability> {
        val scope = CapabilityScope(
            op = OperationType.PUBLISH,
            branch = BRANCH,
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

    private fun successAckResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            {
              "accepted": true,
              "operation": "PUBLISH",
              "branch": "$BRANCH",
              "headSha": "$HEAD_SHA_1",
              "auditRef": "audit-ok-1"
            }
            """.trimIndent(),
        )

    private fun completedLookupResponse(idempotencyKey: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            {
              "id": "$idempotencyKey",
              "operation": "PUBLISH",
              "status": "completed",
              "branch": "$BRANCH",
              "headSha": "$HEAD_SHA_1",
              "auditRef": "audit-lookup-ok",
              "timestamp": 1761840100
            }
            """.trimIndent(),
        )

    private fun branchHeadResponse(headSha: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""{"branch": "$BRANCH", "headSha": "$headSha"}""")

    private fun takeAllRequests(): List<RecordedRequest> {
        val list = mutableListOf<RecordedRequest>()
        while (true) {
            val req = server.takeRequest(10, TimeUnit.MILLISECONDS) ?: break
            list += req
        }
        return list
    }

    private fun List<RecordedRequest>.methodPaths(): List<String> = map { "${it.method} ${it.path}" }

    private fun randomAesKey(): SecretKey {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return SecretKeySpec(bytes, "AES")
    }

    private fun flipLastByte(ciphertext: String): String {
        val payload = Base64.getDecoder().decode(ciphertext.removePrefix(JournalGcm.FORMAT_PREFIX))
        payload[payload.size - 1] = (payload[payload.size - 1].toInt() xor 0x01).toByte()
        return JournalGcm.FORMAT_PREFIX + Base64.getEncoder().encodeToString(payload)
    }

    private class RealGcmJournalCipher(private val secretKey: SecretKey) : JournalFieldCipher {
        override fun encrypt(plaintext: String, associatedData: ByteArray): String =
            JournalGcm.encryptToWire(secretKey, plaintext, associatedData)

        override fun decrypt(ciphertext: String, associatedData: ByteArray): String =
            JournalGcm.decryptFromWire(secretKey, ciphertext, associatedData)
    }

    private class FaultyJournalDao : OperationJournalDao {
        private val delegate = InMemoryOperationJournalDao()
        private val tamperOverrides = linkedMapOf<String, OperationJournalEntity>()
        var crashBeforeCommit: (() -> Unit)? = null

        fun overwriteForFaultInjection(entity: OperationJournalEntity) {
            tamperOverrides[entity.idempotencyKey] = entity
        }

        override suspend fun insert(entry: OperationJournalEntity) {
            delegate.insert(entry)
        }

        override suspend fun commitIntentIfNoActive(
            entry: OperationJournalEntity,
            activeStates: List<OperationJournalState>,
        ): OperationJournalEntity {
            crashBeforeCommit?.invoke()
            return delegate.commitIntentIfNoActive(entry, activeStates)
        }

        override suspend fun updateStateIfCurrent(
            idempotencyKey: String,
            expectedFromState: OperationJournalState,
            toState: OperationJournalState,
            updatedAtEpochMs: Long,
            encryptedAuditRef: String?,
            encryptedResultHeadSha: String?,
            encryptedLastErrorCode: String?,
        ): Int {
            tamperOverrides.remove(idempotencyKey)
            return delegate.updateStateIfCurrent(
                idempotencyKey = idempotencyKey,
                expectedFromState = expectedFromState,
                toState = toState,
                updatedAtEpochMs = updatedAtEpochMs,
                encryptedAuditRef = encryptedAuditRef,
                encryptedResultHeadSha = encryptedResultHeadSha,
                encryptedLastErrorCode = encryptedLastErrorCode,
            )
        }

        override suspend fun transitionState(
            idempotencyKey: String,
            expectedFromState: OperationJournalState,
            toState: OperationJournalState,
            updatedAtEpochMs: Long,
            encryptedAuditRef: String?,
            encryptedResultHeadSha: String?,
            encryptedLastErrorCode: String?,
        ): Boolean {
            tamperOverrides.remove(idempotencyKey)
            return delegate.transitionState(
                idempotencyKey = idempotencyKey,
                expectedFromState = expectedFromState,
                toState = toState,
                updatedAtEpochMs = updatedAtEpochMs,
                encryptedAuditRef = encryptedAuditRef,
                encryptedResultHeadSha = encryptedResultHeadSha,
                encryptedLastErrorCode = encryptedLastErrorCode,
            )
        }

        override suspend fun findByKey(idempotencyKey: String): OperationJournalEntity? =
            tamperOverrides[idempotencyKey] ?: delegate.findByKey(idempotencyKey)

        override suspend fun findByIntentFingerprintAndStates(
            intentFingerprint: String,
            states: List<OperationJournalState>,
        ): OperationJournalEntity? = delegate.findByIntentFingerprintAndStates(intentFingerprint, states)

        override suspend fun findByStates(states: List<OperationJournalState>): List<OperationJournalEntity> =
            delegate.findByStates(states).map { tamperOverrides[it.idempotencyKey] ?: it }

        override suspend fun getAll(): List<OperationJournalEntity> =
            delegate.getAll().map { tamperOverrides[it.idempotencyKey] ?: it }
    }

    private class SimulatedProcessCrash(message: String) : CancellationException(message)

    private companion object {
        const val BRANCH = "task-10008-refactor-parser"
        const val HEAD_SHA_1 = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
        const val SECRET_NONCE = "7f3c9b1e5d2a7046"
    }
}
