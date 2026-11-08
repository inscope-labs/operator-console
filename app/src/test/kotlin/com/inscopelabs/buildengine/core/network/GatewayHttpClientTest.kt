package com.inscopelabs.buildengine.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.inscopelabs.buildengine.data.local.GatewayBaseUrlStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayHttpClientTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient
    private lateinit var activityLog: GatewayActivityLog

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HttpClient(OkHttp) {
            expectSuccess = false
            followRedirects = false
            engine {
                config {
                    followRedirects(false)
                    followSslRedirects(false)
                }
            }
        }
        activityLog = GatewayActivityLog().apply { isEnabled = true }
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun `constructor with GatewayBaseUrlStore injects effective URL`() = runTest {
        val file = File(temporaryFolder.root, "gateway_url.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        val store = GatewayBaseUrlStore(dataStore, debugDefault = server.url("/").toString())

        val gatewayClient = GatewayHttpClient(
            client = client,
            baseUrlStore = store,
            activityLog = activityLog,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val response = gatewayClient.get("/health")
        assertEquals("""{"status":"ok"}""", response)

        val recorded = activityLog.events.value
        assertEquals(1, recorded.size)
        assertEquals(GatewayActivityEvent.Outcome.SUCCESS, recorded[0].outcome)
    }

    @Test
    fun `unconfigured URL throws GatewayNotConfiguredException and records event`() = runTest {
        val gatewayClient = GatewayHttpClient(
            client = client,
            baseUrlProvider = { "" },
            activityLog = activityLog,
        )

        assertThrows(GatewayNotConfiguredException::class.java) {
            kotlinx.coroutines.runBlocking {
                gatewayClient.get("/health")
            }
        }

        val recorded = activityLog.events.value
        assertEquals(1, recorded.size)
        assertEquals(GatewayActivityEvent.Outcome.NOT_CONFIGURED, recorded[0].outcome)
    }

    @Test
    fun `redirect responses are refused with GatewayHttpException`() = runTest {
        val gatewayClient = GatewayHttpClient(
            client = client,
            baseUrlProvider = { server.url("/").toString() },
            activityLog = activityLog,
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://malicious.example.com/steal")
        )

        val exception = assertThrows(GatewayHttpException::class.java) {
            kotlinx.coroutines.runBlocking {
                gatewayClient.get("/tasks")
            }
        }

        assertEquals(302, exception.statusCode)
        assertTrue(exception.message?.contains("redirect") == true)

        val recorded = activityLog.events.value
        assertEquals(1, recorded.size)
        assertEquals(GatewayActivityEvent.Outcome.REFUSED, recorded[0].outcome)
        assertEquals(302, recorded[0].statusCode)
    }

    @Test
    fun `response exceeding Content-Length cap throws GatewayProtocolException`() = runTest {
        val gatewayClient = GatewayHttpClient(
            client = client,
            baseUrlProvider = { server.url("/").toString() },
            activityLog = activityLog,
        )

        val oversizedBody = "x".repeat((GatewayHttpClient.MAX_RESPONSE_BYTES + 1024).toInt())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(oversizedBody)
        )

        val exception = assertThrows(GatewayProtocolException::class.java) {
            kotlinx.coroutines.runBlocking {
                gatewayClient.get("/plans")
            }
        }

        assertTrue(exception.message?.contains("maximum allowed size") == true)

        val recorded = activityLog.events.value
        assertEquals(1, recorded.size)
        assertEquals(GatewayActivityEvent.Outcome.PROTOCOL_ERROR, recorded[0].outcome)
    }

    @Test
    fun `chunked response body exceeding size cap throws GatewayProtocolException`() = runTest {
        val gatewayClient = GatewayHttpClient(
            client = client,
            baseUrlProvider = { server.url("/").toString() },
            activityLog = activityLog,
        )

        val oversizedBody = "x".repeat((GatewayHttpClient.MAX_RESPONSE_BYTES + 1024).toInt())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody(oversizedBody, 4096)
        )

        val exception = assertThrows(GatewayProtocolException::class.java) {
            kotlinx.coroutines.runBlocking {
                gatewayClient.get("/plans")
            }
        }

        assertTrue(exception.message?.contains("maximum allowed size") == true)

        val recorded = activityLog.events.value
        assertEquals(1, recorded.size)
        assertEquals(GatewayActivityEvent.Outcome.PROTOCOL_ERROR, recorded[0].outcome)
    }
}
