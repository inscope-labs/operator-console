package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.result.fold
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.PlanStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The read layer over a real socket: the DTOs the gateway sends, the domain model
 * the console gets, and the refusals it has to tell apart.
 *
 * The bodies below are `docs/gateway-contract.md`'s own examples, verbatim, so the
 * mapping this pins is the mapping the contract describes.
 */
class PlanRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var repository: PlanRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        repository = PlanRepositoryImpl(
            gateway = GatewayRepository(
                httpClient = GatewayHttpClient(httpClient, server.url("/").toString()),
                errorMapper = ErrorMapper(),
            ),
            errorMapper = ErrorMapper(),
        )
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    @Test
    fun `the plan list is mapped onto the domain model`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {
                    "id": "atlas",
                    "title": "Atlas Engine Migration",
                    "status": "in_progress",
                    "taskCount": 14,
                    "completedTaskCount": 8,
                    "updatedAt": 1761840000
                  }
                ]
                """.trimIndent()
            )
        )

        val plans = repository.getPlans().getOrThrow()

        assertEquals(1, plans.size)
        assertEquals("atlas", plans[0].id)
        assertEquals("Atlas Engine Migration", plans[0].title)
        assertEquals(14, plans[0].taskCount)
        assertEquals(8, plans[0].completedTaskCount)
        assertEquals("in_progress", plans[0].status)
    }

    @Test
    fun `one plan is found in the list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(PLAN_LIST))

        val plan = repository.getPlan("atlas").getOrThrow()

        assertEquals("atlas", plan.id)
        assertEquals(14, plan.taskCount)
    }

    @Test
    fun `a plan the gateway does not list is not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(PLAN_LIST))

        val result = repository.getPlan("borealis")

        assertTrue(result.isFailure)
        assertEquals("not_found", result.fold({ "" }, { code, _ -> code }))
    }

    @Test
    fun `the status document is mapped`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "atlas",
                  "status": "in_progress",
                  "halted": false,
                  "openBranches": ["task-10008-refactor-parser"],
                  "blockedTasks": [],
                  "currentMilestone": "M2-Parser"
                }
                """.trimIndent()
            )
        )

        val status: PlanStatus = repository.getStatus("atlas").getOrThrow()

        assertEquals("atlas", status.id)
        assertFalse(status.halted)
        assertEquals(listOf("task-10008-refactor-parser"), status.openBranches)
        assertEquals("M2-Parser", status.currentMilestone)
    }

    @Test
    fun `a halted engine is reported as halted`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "atlas",
                  "status": "in_progress",
                  "halted": true,
                  "openBranches": [],
                  "blockedTasks": ["10009"],
                  "currentMilestone": "M2-Parser"
                }
                """.trimIndent()
            )
        )

        val status = repository.getStatus("atlas").getOrThrow()

        assertTrue(status.halted)
        assertEquals(listOf("10009"), status.blockedTasks)
    }

    @Test
    fun `the validation report keeps its errors and warnings`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "valid": false,
                  "planId": "atlas",
                  "errors": ["work order 3 names no task"],
                  "warnings": ["milestone M3 has no owner"]
                }
                """.trimIndent()
            )
        )

        val validation = repository.validate("atlas").getOrThrow()

        assertFalse(validation.valid)
        assertEquals(listOf("work order 3 names no task"), validation.errors)
        assertEquals(listOf("milestone M3 has no owner"), validation.warnings)
    }

    @Test
    fun `the render check keeps its issues`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "ok": false,
                  "planId": "atlas",
                  "renderIssues": ["the M2 view is stale"]
                }
                """.trimIndent()
            )
        )

        val check = repository.renderCheck("atlas").getOrThrow()

        assertFalse(check.ok)
        assertEquals(listOf("the M2 view is stale"), check.renderIssues)
    }

    @Test
    fun `a refused credential is an authentication failure`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"code":"OPERATOR_AUTH","message":"the console's credential is not accepted"}"""
            )
        )

        val result = repository.getPlans()

        assertTrue(result.isFailure)
        assertEquals("authentication", result.fold({ "" }, { code, _ -> code }))
    }

    @Test
    fun `the gateway's own reason survives in the message`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"code":"NOT_ENROLLED","message":"this device is not enrolled"}"""
            )
        )

        val message = repository.getPlans().fold({ "" }, { _, message -> message })

        assertTrue(message, message.contains("NOT_ENROLLED"))
        assertTrue(message, message.contains("this device is not enrolled"))
    }

    @Test
    fun `an answer the console cannot read is a protocol failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("this is not json"))

        val result = repository.getPlans()

        assertTrue(result.isFailure)
        assertEquals("unknown", result.fold({ "" }, { code, _ -> code }))
    }

    @Test
    fun `an unknown field in the answer is ignored`() = runTest {
        // The contract requires it (§1.5), so that a gateway which adds a field
        // does not break an older console.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {
                    "id": "atlas",
                    "title": "Atlas Engine Migration",
                    "status": "in_progress",
                    "taskCount": 14,
                    "completedTaskCount": 8,
                    "updatedAt": 1761840000,
                    "somethingNew": {"nested": true}
                  }
                ]
                """.trimIndent()
            )
        )

        val plans = repository.getPlans().getOrThrow()

        assertEquals("atlas", plans[0].id)
    }

    private companion object {
        val PLAN_LIST = """
            [
              {
                "id": "atlas",
                "title": "Atlas Engine Migration",
                "status": "in_progress",
                "taskCount": 14,
                "completedTaskCount": 8,
                "updatedAt": 1761840000
              }
            ]
        """.trimIndent()
    }
}
