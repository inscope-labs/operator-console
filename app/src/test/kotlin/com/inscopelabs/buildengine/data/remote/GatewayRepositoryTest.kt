package com.inscopelabs.buildengine.data.remote

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayFailureState
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.EngineScopeRequestDto
import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.PublishRequestDto
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the gateway surface against a real socket: the request the console makes,
 * the answer it reads, and the refusals it has to tell apart.
 *
 * The gateway is the sole enforcement point, so the contract this pins is the one
 * in docs/gateway-contract.md — the answers below are that document's own
 * examples, and the paths are its own routes.
 */
class GatewayRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var repository: GatewayRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        repository = GatewayRepository(
            httpClient = GatewayHttpClient(httpClient, server.url("/").toString()),
            errorMapper = ErrorMapper(),
        )
    }

    @After
    fun tearDown() {
        httpClient.close()
        server.shutdown()
    }

    // -- the read surface --------------------------------------------------------

    @Test
    fun `the plan list is read from the gateway`() = runTest {
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

        val plans = repository.listPlans()

        assertEquals(1, plans.size)
        assertEquals("atlas", plans[0].id)
        assertEquals("Atlas Engine Migration", plans[0].title)
        assertEquals("in_progress", plans[0].status)
        assertEquals(14, plans[0].taskCount)
        assertEquals(8, plans[0].completedTaskCount)
        assertEquals("/plans", server.takeRequest().path)
    }

    @Test
    fun `a plan id is percent-encoded so it stays one path segment`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id": "atlas", "status": "in_progress", "halted": false, "openBranches": []}"""
            )
        )

        repository.planStatus("atlas/../main")

        assertEquals("/plans/atlas%2F..%2Fmain/status", server.takeRequest().path)
    }

    @Test
    fun `the plan status is read from the gateway`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "atlas",
                  "status": "in_progress",
                  "halted": true,
                  "openBranches": ["task-10008-refactor-parser"],
                  "blockedTasks": [],
                  "currentMilestone": "M2-Parser"
                }
                """.trimIndent()
            )
        )

        val status = repository.planStatus("atlas")

        assertTrue(status.halted)
        assertEquals(listOf("task-10008-refactor-parser"), status.openBranches)
        assertEquals("M2-Parser", status.currentMilestone)
        assertEquals("/plans/atlas/status", server.takeRequest().path)
    }

    @Test
    fun `a plan is validated by posting to the validate route`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"valid": true, "planId": "atlas", "errors": [], "warnings": []}"""
            )
        )

        val validation = repository.validatePlan("atlas")

        assertTrue(validation.valid)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/plans/atlas/validate", recorded.path)
    }

    @Test
    fun `the next tasks are read from the gateway`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
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
            )
        )

        val tasks = repository.nextTasks()

        assertEquals(1, tasks.size)
        assertEquals("10008", tasks[0].id)
        assertEquals("atlas", tasks[0].planId)
        assertEquals("task-10008-refactor-parser", tasks[0].branch)
        assertEquals("ready", tasks[0].status)
        assertEquals("/tasks/next", server.takeRequest().path)
    }

    @Test
    fun `the branch head is read from the gateway`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "branch": "task-10008-refactor-parser",
                  "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
                }
                """.trimIndent()
            )
        )

        val head = repository.branchHead("task-10008-refactor-parser")

        assertEquals("9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d", head.headSha)
        assertEquals("/branches/task-10008-refactor-parser/head", server.takeRequest().path)
    }

    @Test
    fun `the doctor report is read from the gateway`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "healthy": true,
                  "checks": [
                    {"name": "github_token_validity", "passed": true},
                    {"name": "halt_state", "passed": true, "details": "Engine active"}
                  ]
                }
                """.trimIndent()
            )
        )

        val report = repository.doctor()

        assertTrue(report.healthy)
        assertEquals(2, report.checks.size)
        assertEquals("github_token_validity", report.checks[0].name)
        assertEquals("Engine active", report.checks[1].details)
        assertEquals("/doctor", server.takeRequest().path)
    }

    // -- the write surface -------------------------------------------------------

    @Test
    fun `a publish carries its capability and names the task`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "accepted": true,
                  "operation": "PUBLISH",
                  "branch": "task-10008-refactor-parser",
                  "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
                  "auditRef": "audit-20261030-98234"
                }
                """.trimIndent()
            )
        )

        val request = PublishRequestDto(
            plan = "atlas",
            task = "10008",
            branch = "task-10008-refactor-parser",
            head = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
            capability = CAPABILITY,
        )

        val ack = repository.publish(request)

        assertTrue(ack.accepted)
        assertEquals("PUBLISH", ack.operation)
        assertEquals("audit-20261030-98234", ack.auditRef)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/tasks/10008/publish", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("the capability must travel with the request", body.contains("\"capability\""))
        assertTrue("the capability must travel with the request", body.contains("7f3c9b1e5d2a7046"))
        assertTrue("the branch must be named in the request", body.contains("task-10008-refactor-parser"))
    }

    @Test
    fun `a halt is scoped to the engine branch and carries its reason`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"accepted": true, "operation": "HALT", "branch": "engine", "auditRef": "audit-1"}"""
            )
        )

        repository.halt(
            EngineScopeRequestDto(
                branch = "engine",
                head = "a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e",
                reason = "Security review in progress",
                capability = CAPABILITY,
            )
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/halt", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("Security review in progress"))
    }

    // -- refusals ----------------------------------------------------------------

    @Test
    fun `a refusal carries the gateway's own code and reason`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"code": "HEAD_DRIFT", "message": "the branch has advanced"}"""
            )
        )

        val failure = failureOf { repository.listPlans() }

        assertTrue(failure is GatewayHttpException)
        val http = failure as GatewayHttpException
        assertEquals(409, http.statusCode)
        assertTrue(http.body!!.contains("HEAD_DRIFT"))
    }

    @Test
    fun `the four V5-V8 rejections are distinct states`() {
        val mapper = ErrorMapper()

        val scope = mapper.map(403, """{"code": "BRANCH_MISMATCH", "message": "wrong branch"}""")
        val replay = mapper.map(409, """{"code": "NONCE_REPLAYED", "message": "nonce seen"}""")
        val drift = mapper.map(409, """{"code": "HEAD_DRIFT", "message": "the branch moved"}""")
        val halt = mapper.map(423, """{"code": "HALT_ACTIVE", "message": "the engine is halted"}""")

        assertEquals(GatewayFailureState.SCOPE_REFUSED.code, scope.code)
        assertEquals(GatewayFailureState.REPLAYED.code, replay.code)
        assertEquals(GatewayFailureState.HEAD_DRIFTED.code, drift.code)
        assertEquals(GatewayFailureState.HALT_ACTIVE.code, halt.code)

        assertEquals(4, setOf(scope.code, replay.code, drift.code, halt.code).size)
    }

    @Test
    fun `the gateway's code survives into the message`() {
        val mapper = ErrorMapper()

        val failure = mapper.map(409, """{"code": "HEAD_DRIFT", "message": "the branch moved"}""")

        assertTrue(failure.message.contains("HEAD_DRIFT"))
        assertTrue(failure.message.contains("the branch moved"))
    }

    @Test
    fun `a guard failure keeps its detail`() {
        val mapper = ErrorMapper()

        val failure = mapper.map(
            422,
            """{"code": "GUARD_FAILED:G3_TOUCHES_PROTECTED_PATH", "message": "the diff touches engine/"}""",
        )

        assertEquals(GatewayFailureState.GUARD_FAILED.code, failure.code)
        assertTrue(failure.message.contains("GUARD_FAILED"))
    }

    @Test
    fun `an unrecognised gateway code is surfaced, not flattened`() {
        val mapper = ErrorMapper()

        val failure = mapper.map(400, """{"code": "SOMETHING_NEW", "message": "brand new"}""")

        assertEquals(GatewayFailureState.UNKNOWN.code, failure.code)
        assertTrue(failure.message.contains("SOMETHING_NEW"))
    }

    @Test
    fun `the status alone cannot tell a replay from a drift`() {
        val mapper = ErrorMapper()

        assertEquals(GatewayFailureState.CONFLICT.code, mapper.mapStatusCode(409))
    }

    @Test
    fun `a refusal without a readable body falls back to the status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("<html>gateway down</html>"))

        val failure = failureOf { repository.listPlans() }

        assertTrue(failure is GatewayHttpException)
        assertEquals(503, (failure as GatewayHttpException).statusCode)
    }

    @Test
    fun `an answer the console cannot read is its own failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))

        val failure = failureOf { repository.listPlans() }

        assertTrue(failure is GatewayProtocolException)
    }

    @Test
    fun `an unknown field in the answer is ignored`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {
                    "id": "atlas",
                    "title": "Atlas",
                    "taskCount": 12,
                    "somethingNew": true
                  }
                ]
                """.trimIndent()
            )
        )

        val plan = repository.getPlan("atlas")

        assertEquals("atlas", plan.id)
        assertEquals(12, plan.taskCount)
    }

    // -- the preflight -----------------------------------------------------------

    @Test
    fun `the preflight reports the gateway's environment`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status": "ok", "environment": "test", "version": "1.0.0", "timestamp": 1761840000}"""
            )
        )

        val result = repository.health()

        assertTrue(result.isSuccess)
        val health = (result as AppResult.Success).value
        assertEquals("test", health.environment)
        assertEquals("/health", server.takeRequest().path)
    }

    @Test
    fun `the preflight reports the gateway's reason when it fails`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(423).setBody(
                """{"code": "HALT_ACTIVE", "message": "the engine is halted"}"""
            )
        )

        val result = repository.health()

        assertTrue(result.isFailure)
        val failure = result as AppResult.Failure
        assertEquals(GatewayFailureState.HALT_ACTIVE.code, failure.code)
        assertTrue(failure.message.contains("the engine is halted"))
    }

    @Test
    fun `the preflight falls back to the status when the body says nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody(""))

        val result = repository.health()

        assertTrue(result.isFailure)
        assertEquals(
            GatewayFailureState.GATEWAY_ERROR.code,
            (result as AppResult.Failure).code,
        )
    }

    @Test
    fun `the DTOs are what the repository returns`() {
        // The wire types are the contract with the gateway; the domain types are
        // `data.repository`'s job, not this one's.
        assertEquals(
            "atlas",
            PlanSummaryDto(id = "atlas", title = "Atlas", taskCount = 1).id,
        )
        assertEquals(
            "",
            TaskSummaryDto(id = "10008", planId = "atlas").status,
        )
        assertNull(PublishRequestDto().capability.ifEmpty { null })
    }

    private suspend fun <T> failureOf(block: suspend () -> T): Throwable =
        runCatching { block() }.exceptionOrNull()
            ?: error("expected the call to fail")

    private companion object {
        /** The capability from docs/gateway-contract.md §1.2. */
        const val CAPABILITY: String =
            "ctxop-v1\nk2\n1761840000\norder-7731\natlas\n10008\nPUBLISH\n" +
                "task-10008-refactor-parser\n9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d\n" +
                "7f3c9b1e5d2a7046\nsig_base64url_raw_rs"
    }
}
