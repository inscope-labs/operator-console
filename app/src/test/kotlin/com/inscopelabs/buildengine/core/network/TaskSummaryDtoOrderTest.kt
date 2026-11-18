package com.inscopelabs.buildengine.core.network

import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.data.repository.TaskRepositoryImpl
import com.inscopelabs.buildengine.domain.usecase.write.TaskFieldPublishOrderResolver
import com.inscopelabs.buildengine.manualpipeline.data.gateway.GatewayATaskReadAdapter
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.TaskReadMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract conformance for `TaskSummaryDto.order` on `GET /tasks/next`
 * (docs/gateway-contract.md §3.6, contract revision 1.2, ADR 0014 D1 / ADR 0015).
 *
 * A task item without a non-blank `order` fails closed at deserialization and transport
 * boundaries so the console never reads a ready task with an empty or invented order.
 */
class TaskSummaryDtoOrderTest {

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var gatewayRepository: GatewayRepository
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var manualReadAdapter: GatewayATaskReadAdapter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        gatewayRepository = GatewayRepository(
            httpClient = GatewayHttpClient(httpClient, baseUrlProvider = { server.url("/").toString() }),
            errorMapper = ErrorMapper(),
        )
        taskRepository = TaskRepositoryImpl(
            gateway = gatewayRepository,
            errorMapper = ErrorMapper(),
        )
        manualReadAdapter = GatewayATaskReadAdapter(gatewayRepository)
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    @Test
    fun `payload with non-blank order decodes and preserves order through domain and manual mappers`() = runTest {
        val dto = json.decodeFromString<List<TaskSummaryDto>>(VALID_TASKS_NEXT_JSON).single()
        assertEquals("order-7731", dto.order)

        server.enqueue(MockResponse().setResponseCode(200).setBody(VALID_TASKS_NEXT_JSON))
        val wireTasks = gatewayRepository.nextTasks()
        assertEquals(1, wireTasks.size)
        assertEquals("order-7731", wireTasks.single().order)

        server.enqueue(MockResponse().setResponseCode(200).setBody(VALID_TASKS_NEXT_JSON))
        val domainResult = taskRepository.getNextTasks()
        assertTrue(domainResult is AppResult.Success)
        val domainTask = (domainResult as AppResult.Success).value.single()
        assertEquals("order-7731", domainTask.order)
        assertEquals("order-7731", TaskFieldPublishOrderResolver().resolve(domainTask))

        val manualTask = TaskReadMapper.toDomain(dto)
        assertEquals("order-7731", manualTask.orderId)
    }

    @Test
    fun `payload missing order fails closed at DTO decode, gateway, and repository layers`() = runTest {
        val decodeError = runCatching {
            json.decodeFromString<List<TaskSummaryDto>>(MISSING_ORDER_TASKS_NEXT_JSON)
        }.exceptionOrNull()
        assertTrue(
            "Expected SerializationException when order is absent, got $decodeError",
            decodeError is SerializationException,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody(MISSING_ORDER_TASKS_NEXT_JSON))
        val gatewayError = runCatching {
            gatewayRepository.nextTasks()
        }.exceptionOrNull()
        assertTrue(
            "Expected GatewayProtocolException when order is absent, got $gatewayError",
            gatewayError is GatewayProtocolException,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody(MISSING_ORDER_TASKS_NEXT_JSON))
        val repoResult = taskRepository.getNextTasks()
        assertTrue(repoResult is AppResult.Failure)
        assertEquals(GatewayFailureState.UNKNOWN.code, (repoResult as AppResult.Failure).code)

        server.enqueue(MockResponse().setResponseCode(200).setBody(MISSING_ORDER_TASKS_NEXT_JSON))
        val manualResult = manualReadAdapter.fetchNextTasks()
        assertTrue(manualResult.isFailure)
        assertTrue(manualResult.exceptionOrNull() is GatewayProtocolException)
    }

    @Test
    fun `payload with blank or whitespace order fails closed everywhere`() = runTest {
        for (blankOrder in listOf("", "   ", "\t\n")) {
            val payload = taskArrayWithOrderLiteral("\"$blankOrder\"")
            val directError = runCatching {
                TaskSummaryDto(id = "10008", planId = "atlas", order = blankOrder)
            }.exceptionOrNull()
            assertTrue(
                "Expected IllegalArgumentException for blank direct order '$blankOrder', got $directError",
                directError is IllegalArgumentException,
            )

            val decodeError = runCatching {
                json.decodeFromString<List<TaskSummaryDto>>(payload)
            }.exceptionOrNull()
            assertTrue(
                "Expected IllegalArgumentException decoding blank order '$blankOrder', got $decodeError",
                decodeError is IllegalArgumentException,
            )

            server.enqueue(MockResponse().setResponseCode(200).setBody(payload))
            val gatewayError = runCatching {
                gatewayRepository.nextTasks()
            }.exceptionOrNull()
            assertTrue(
                "Expected GatewayProtocolException for blank order '$blankOrder', got $gatewayError",
                gatewayError is GatewayProtocolException,
            )

            server.enqueue(MockResponse().setResponseCode(200).setBody(payload))
            val repoResult = taskRepository.getNextTasks()
            assertTrue(repoResult is AppResult.Failure)
            assertEquals(GatewayFailureState.UNKNOWN.code, (repoResult as AppResult.Failure).code)
        }
    }

    @Test
    fun `payload with null order fails closed`() = runTest {
        val nullOrderPayload = taskArrayWithOrderLiteral("null")

        val decodeError = runCatching {
            json.decodeFromString<List<TaskSummaryDto>>(nullOrderPayload)
        }.exceptionOrNull()
        assertTrue(
            "Expected SerializationException decoding null order, got $decodeError",
            decodeError is SerializationException,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody(nullOrderPayload))
        val repoResult = taskRepository.getNextTasks()
        assertTrue(repoResult is AppResult.Failure)
        assertEquals(GatewayFailureState.UNKNOWN.code, (repoResult as AppResult.Failure).code)
    }

    @Test
    fun `array where a second task omits order fails closed rather than dropping or inventing order`() = runTest {
        val mixedPayload = """
            [
              {
                "id": "10008",
                "planId": "atlas",
                "title": "Refactor parser",
                "branch": "task-10008-refactor-parser",
                "status": "ready",
                "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
                "order": "order-7731"
              },
              {
                "id": "10009",
                "planId": "atlas",
                "title": "Missing order item",
                "branch": "task-10009-missing-order",
                "status": "ready",
                "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
              }
            ]
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(mixedPayload))
        val repoResult = taskRepository.getNextTasks()
        assertTrue(repoResult is AppResult.Failure)
        assertEquals(GatewayFailureState.UNKNOWN.code, (repoResult as AppResult.Failure).code)
    }

    private fun taskArrayWithOrderLiteral(orderJsonLiteral: String): String = """
        [
          {
            "id": "10008",
            "planId": "atlas",
            "title": "Refactor parser",
            "branch": "task-10008-refactor-parser",
            "status": "ready",
            "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
            "order": $orderJsonLiteral
          }
        ]
    """.trimIndent()

    private companion object {
        val VALID_TASKS_NEXT_JSON = """
            [
              {
                "id": "10008",
                "planId": "atlas",
                "title": "Refactor parser",
                "branch": "task-10008-refactor-parser",
                "status": "ready",
                "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
                "order": "order-7731"
              }
            ]
        """.trimIndent()

        val MISSING_ORDER_TASKS_NEXT_JSON = """
            [
              {
                "id": "10008",
                "planId": "atlas",
                "title": "Refactor parser",
                "branch": "task-10008-refactor-parser",
                "status": "ready",
                "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
              }
            ]
        """.trimIndent()
    }
}
