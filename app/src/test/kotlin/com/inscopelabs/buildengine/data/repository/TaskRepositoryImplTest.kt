package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.result.fold
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.TaskStatus
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
 * The task read layer over a real socket.
 *
 * The status translation is the part worth pinning: the gateway says `"ready"`,
 * the console says `PENDING`, and a status the console does not know must not be
 * turned into a guess about whether work has started.
 */
class TaskRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = HttpClient(OkHttp) { expectSuccess = false }
        repository = TaskRepositoryImpl(
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
    fun `the next tasks are mapped onto the domain model`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TASK_LIST))

        val tasks = repository.getNextTasks().getOrThrow()

        assertEquals(1, tasks.size)
        assertEquals("10008", tasks[0].id)
        assertEquals("atlas", tasks[0].planId)
        assertEquals("Refactor parser", tasks[0].title)
        assertEquals("task-10008-refactor-parser", tasks[0].branch)
        assertEquals("9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d", tasks[0].headSha)
    }

    @Test
    fun `the gateway's ready becomes pending`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TASK_LIST))

        val tasks = repository.getNextTasks().getOrThrow()

        assertEquals(TaskStatus.PENDING, tasks[0].status)
    }

    @Test
    fun `the gateway's unassigned becomes pending`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(taskListWithStatus("unassigned"))
        )

        val tasks = repository.getNextTasks().getOrThrow()

        assertEquals(TaskStatus.PENDING, tasks[0].status)
    }

    @Test
    fun `the gateway's in_progress becomes in progress`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(taskListWithStatus("in_progress"))
        )

        val tasks = repository.getNextTasks().getOrThrow()

        assertEquals(TaskStatus.IN_PROGRESS, tasks[0].status)
    }

    @Test
    fun `the gateway's done becomes done`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(taskListWithStatus("done"))
        )

        val tasks = repository.getNextTasks().getOrThrow()

        assertEquals(TaskStatus.DONE, tasks[0].status)
    }

    @Test
    fun `a status the console does not know is unknown, not a guess`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(taskListWithStatus("awaiting-review"))
        )

        val tasks = repository.getNextTasks().getOrThrow()

        assertEquals(TaskStatus.UNKNOWN, tasks[0].status)
    }

    @Test
    fun `one task is found in the list`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TASK_LIST))

        val task = repository.getTask("10008").getOrThrow()

        assertEquals("10008", task.id)
        assertEquals("task-10008-refactor-parser", task.branch)
    }

    @Test
    fun `a task the gateway does not hand out is not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TASK_LIST))

        val result = repository.getTask("99999")

        assertTrue(result.isFailure)
        assertEquals("not_found", result.fold({ "" }, { code, _ -> code }))
    }

    @Test
    fun `the branch carries the live head, not the list's snapshot`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(TASK_LIST))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "branch": "task-10008-refactor-parser",
                  "headSha": "11223344556677889900aabbccddeeff11223344"
                }
                """.trimIndent()
            )
        )

        val branch = repository.branchFor("10008").getOrThrow()

        assertEquals("task-10008-refactor-parser", branch.name)
        assertEquals("11223344556677889900aabbccddeeff11223344", branch.headSha)
    }

    @Test
    fun `the guard verdict is left unknown`() = runTest {
        // The console evaluates no guards and no read endpoint reports their
        // outcome, so it does not claim either way.
        server.enqueue(MockResponse().setResponseCode(200).setBody(TASK_LIST))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"branch":"task-10008-refactor-parser","headSha":"a1b2c3"}"""
            )
        )

        val branch = repository.branchFor("10008").getOrThrow()

        assertNull(branch.guardsPassed)
    }

    @Test
    fun `a task with no branch has no branch to resolve`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {
                    "id": "10008",
                    "planId": "atlas",
                    "title": "Refactor parser",
                    "branch": "",
                    "status": "ready",
                    "headSha": ""
                  }
                ]
                """.trimIndent()
            )
        )

        val result = repository.branchFor("10008")

        assertTrue(result.isFailure)
        assertEquals("not_found", result.fold({ "" }, { code, _ -> code }))
    }

    @Test
    fun `a status change is refused because the contract has no route for it`() = runTest {
        val result = repository.setStatus("10008", TaskStatus.DONE)

        assertTrue(result.isFailure)
        assertEquals("unsupported_operation", result.fold({ "" }, { code, _ -> code }))
    }

    @Test
    fun `a refused credential is an authentication failure`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"code":"OPERATOR_AUTH","message":"not accepted"}"""
            )
        )

        val result = repository.getNextTasks()

        assertTrue(result.isFailure)
        assertEquals("authentication", result.fold({ "" }, { code, _ -> code }))
    }

    private fun taskListWithStatus(status: String): String = """
        [
          {
            "id": "10008",
            "planId": "atlas",
            "title": "Refactor parser",
            "branch": "task-10008-refactor-parser",
            "status": "$status",
            "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
          }
        ]
    """.trimIndent()

    private companion object {
        val TASK_LIST = """
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
