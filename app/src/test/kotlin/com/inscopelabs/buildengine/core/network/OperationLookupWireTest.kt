package com.inscopelabs.buildengine.core.network

import com.inscopelabs.buildengine.core.network.dto.EngineScopeRequestDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.network.dto.ReconcileRequestDto
import com.inscopelabs.buildengine.core.network.dto.UploadRequestDto
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies wire serialization of `idempotencyKey` on write DTOs and decoding of
 * `GET /operations/{id}` responses against docs/gateway-contract.md §1.6 & §3.19.
 */
class OperationLookupWireTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var repository: GatewayRepository

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        repository = GatewayRepository(
            httpClient = GatewayHttpClient(httpClient, baseUrlProvider = { server.url("/").toString() }),
            errorMapper = ErrorMapper(),
        )
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    @Test
    fun `write request DTOs serialize idempotencyKey per schemas`() = runTest {
        val key = "3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5"

        val publishJson = json.encodeToString(
            PublishRequestDto(
                plan = "atlas",
                task = "10008",
                branch = "task-10008-refactor-parser",
                head = SAMPLE_HEAD,
                capability = SAMPLE_CAP,
                idempotencyKey = key,
            ),
        )
        assertTrue(publishJson.contains("\"idempotencyKey\":\"$key\""))

        val uploadJson = json.encodeToString(
            UploadRequestDto(
                plan = "atlas",
                task = "10008",
                branch = "task-10008-refactor-parser",
                head = SAMPLE_HEAD,
                submissionId = "sub-1",
                zipSha256 = "a".repeat(64),
                zipBase64 = "UEsDBBQ=",
                capability = SAMPLE_CAP,
                idempotencyKey = key,
            ),
        )
        assertTrue(uploadJson.contains("\"idempotencyKey\":\"$key\""))

        val reconcileJson = json.encodeToString(
            ReconcileRequestDto(
                plan = "atlas",
                task = "10008",
                branch = "task-10008-refactor-parser",
                head = SAMPLE_HEAD,
                submissionId = "sub-1",
                capability = SAMPLE_CAP,
                idempotencyKey = key,
            ),
        )
        assertTrue(reconcileJson.contains("\"idempotencyKey\":\"$key\""))

        val haltJson = json.encodeToString(
            EngineScopeRequestDto(
                branch = "engine",
                head = SAMPLE_HEAD,
                reason = "Manual halt",
                capability = SAMPLE_CAP,
                idempotencyKey = key,
            ),
        )
        assertTrue(haltJson.contains("\"idempotencyKey\":\"$key\""))

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"accepted": true, "operation": "PUBLISH", "branch": "task-10008-refactor-parser", "headSha": "$SAMPLE_HEAD", "auditRef": "audit-1"}""",
            ),
        )
        repository.publish(
            PublishRequestDto(
                plan = "atlas",
                task = "10008",
                branch = "task-10008-refactor-parser",
                head = SAMPLE_HEAD,
                capability = SAMPLE_CAP,
                idempotencyKey = key,
            ),
        )
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/tasks/10008/publish", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"idempotencyKey\":\"$key\""))
    }

    @Test
    fun `lookupOperation decodes completed response and path-encodes idempotencyKey`() = runTest {
        val key = "3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "$key",
                  "operation": "PUBLISH",
                  "status": "completed",
                  "branch": "task-10008-refactor-parser",
                  "headSha": "$SAMPLE_HEAD",
                  "auditRef": "audit-20261030-98234",
                  "timestamp": 1761840000,
                  "futureField": "ignored"
                }
                """.trimIndent(),
            ),
        )

        val dto = repository.lookupOperation(key)

        assertEquals(key, dto.id)
        assertEquals("PUBLISH", dto.operation)
        assertEquals("completed", dto.status)
        assertEquals("task-10008-refactor-parser", dto.branch)
        assertEquals(SAMPLE_HEAD, dto.headSha)
        assertEquals("audit-20261030-98234", dto.auditRef)
        assertEquals(1761840000L, dto.timestamp)
        assertNull(dto.code)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/operations/$key", recorded.path)
    }

    @Test
    fun `lookupOperation decodes refused and pending responses`() = runTest {
        val key = "3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "$key",
                  "operation": "PUBLISH",
                  "status": "refused",
                  "timestamp": 1761840000,
                  "code": "HEAD_DRIFT",
                  "auditRef": "audit-99"
                }
                """.trimIndent(),
            ),
        )
        val refused = repository.lookupOperation(key)
        assertEquals("refused", refused.status)
        assertEquals("HEAD_DRIFT", refused.code)
        assertEquals("audit-99", refused.auditRef)

        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "$key",
                  "operation": "PUBLISH",
                  "status": "pending",
                  "timestamp": 1761840001
                }
                """.trimIndent(),
            ),
        )
        val pending = repository.lookupOperation(key)
        assertEquals("pending", pending.status)
        assertNull(pending.code)
        assertNull(pending.headSha)
        assertNull(pending.auditRef)
    }

    @Test
    fun `lookupOperation 404 throws GatewayHttpException with OPERATION_NOT_FOUND`() = runTest {
        val key = "3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5"
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"code": "OPERATION_NOT_FOUND", "message": "No record for key"}""",
            ),
        )

        val err = runCatching { repository.lookupOperation(key) }.exceptionOrNull()
        assertTrue(err is GatewayHttpException)
        val http = err as GatewayHttpException
        assertEquals(404, http.statusCode)
        assertTrue(http.body!!.contains("OPERATION_NOT_FOUND"))
    }

    private companion object {
        const val SAMPLE_HEAD = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
        const val SAMPLE_CAP =
            "ctxop-v1\nk2\n1761840000\norder-7731\natlas\n10008\nPUBLISH\n" +
                "task-10008-refactor-parser\n$SAMPLE_HEAD\n7f3c9b1e5d2a7046\nsig_base64url_raw_rs"
    }
}
