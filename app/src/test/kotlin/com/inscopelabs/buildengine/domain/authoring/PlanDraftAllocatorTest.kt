package com.inscopelabs.buildengine.domain.authoring

import com.inscopelabs.buildengine.domain.model.PlanDependency
import com.inscopelabs.buildengine.domain.model.PlanDependencyType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Identifier derivation and materialisation tests.
 *
 * The canonical rule under test: a task identifier is `NNNNN-PHASE-TTT-SS`, the stage is the
 * hundreds digit of the task number, and the identifier is derived rather than typed.
 */
class PlanDraftAllocatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the first task of a stage carries the stage's hundreds digit`() {
        val draft = PlanDraftFixtures.draft()

        assertEquals("10001-1-100-00", PlanDraftAllocator.taskId(draft, draft.tasks.single()))
    }

    @Test
    fun `the second task of a stage carries the next task number`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(
                PlanDraftFixtures.task(localId = "a", ordinal = 0),
                PlanDraftFixtures.task(localId = "b", ordinal = 1),
            ),
        )

        assertEquals("10001-1-100-00", PlanDraftAllocator.taskId(draft, draft.tasks[0]))
        assertEquals("10001-1-101-00", PlanDraftAllocator.taskId(draft, draft.tasks[1]))
    }

    @Test
    fun `a task in a later phase carries that phase`() {
        val draft = PlanDraftFixtures.draft(
            phases = listOf(PlanDraftFixtures.phase("1"), PlanDraftFixtures.phase("2")),
            stages = listOf(PlanDraftFixtures.stage("1-1"), PlanDraftFixtures.stage("2-1")),
            tasks = listOf(PlanDraftFixtures.task(stageId = "2-1")),
        )

        assertEquals("10001-2-100-00", PlanDraftAllocator.taskId(draft, draft.tasks.single()))
    }

    @Test
    fun `an unreserved plan number leaves the identifier visibly provisional`() {
        val draft = PlanDraftFixtures.draft(planNumber = "")

        val identifier = PlanDraftAllocator.taskId(draft, draft.tasks.single())

        assertTrue(identifier.startsWith("<NNNNN>-"))
        assertFalse(draft.isPlanNumberReserved)
    }

    @Test
    fun `a task with no known stage has no number`() {
        val draft = PlanDraftFixtures.draft(tasks = listOf(PlanDraftFixtures.task(stageId = "")))

        assertNull(PlanDraftAllocator.taskNumber(draft, draft.tasks.single()))
    }

    @Test
    fun `the next free ordinal does not collide with an existing task`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(
                PlanDraftFixtures.task(localId = "a", ordinal = 0),
                PlanDraftFixtures.task(localId = "b", ordinal = 1),
            ),
        )

        assertEquals(2, PlanDraftAllocator.nextFreeOrdinal(draft, "1-1"))
    }

    @Test
    fun `a task dependency is written as the canonical identifier`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(
                PlanDraftFixtures.task(localId = "a", ordinal = 0),
                PlanDraftFixtures.task(
                    localId = "b",
                    ordinal = 1,
                    blockedBy = listOf(PlanDependency(type = PlanDependencyType.TASK, ref = "a")),
                ),
            ),
        )

        val resolved = PlanDraftAllocator.resolveDependencyRef(draft, draft.tasks[1].blockedBy.single())

        assertEquals("10001-1-100-00", resolved)
    }

    @Test
    fun `a decision dependency is written as it is`() {
        val draft = PlanDraftFixtures.draft()
        val dependency = PlanDependency(type = PlanDependencyType.DECISION, ref = "D-12")

        assertEquals("D-12", PlanDraftAllocator.resolveDependencyRef(draft, dependency))
    }

    // -- materialisation --

    @Test
    fun `the materialisation is the canonical plan directory`() {
        val draft = PlanDraftFixtures.draft()

        val paths = PlanDraftAllocator.materialize(draft).map { it.path }

        assertTrue(paths.contains("plans/my-plan/plan.json"))
        assertTrue(paths.contains("plans/my-plan/stages.json"))
        assertTrue(paths.contains("plans/my-plan/tasks/10001-1-100-00.json"))
        assertTrue(paths.contains("plans/my-plan/coverage_exempt.json"))
        assertTrue(paths.contains("plans/my-plan/PLAN.md"))
        assertTrue(paths.contains("plans/my-plan/CONCURRENCY.md"))
    }

    @Test
    fun `the generated files are marked as generated`() {
        val draft = PlanDraftFixtures.draft()

        val generated = PlanDraftAllocator.materialize(draft).filter { it.kind == PlannedFileKind.GENERATED }

        assertEquals(2, generated.size)
        assertTrue(generated.all { it.content.contains("never authored by hand") })
    }

    @Test
    fun `the plan document carries the canonical fields`() {
        val draft = PlanDraftFixtures.draft(deliverableRoots = listOf("src/"))

        val planJson = fileJson(draft, "plans/my-plan/plan.json")

        assertEquals("my-plan", planJson["plan_id"]?.jsonPrimitive?.content)
        assertEquals("My plan", planJson["title"]?.jsonPrimitive?.content)
        assertEquals("10001", planJson["plan_number"]?.jsonPrimitive?.content)
        assertEquals(listOf("src/"), planJson["deliverable_roots"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    @Test
    fun `the stages document carries phases and stages`() {
        val draft = PlanDraftFixtures.draft()

        val stagesJson = fileJson(draft, "plans/my-plan/stages.json")

        assertEquals(1, stagesJson["phases"]?.jsonArray?.size)
        assertEquals("1", stagesJson["phases"]?.jsonArray?.get(0)?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals("1-1", stagesJson["stages"]?.jsonArray?.get(0)?.jsonObject?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun `the task document carries the canonical fields`() {
        val draft = PlanDraftFixtures.draft()

        val taskJson = fileJson(draft, "plans/my-plan/tasks/10001-1-100-00.json")

        assertEquals("10001-1-100-00", taskJson["id"]?.jsonPrimitive?.content)
        assertEquals("Do the thing", taskJson["title"]?.jsonPrimitive?.content)
        assertEquals("1-1", taskJson["stage"]?.jsonPrimitive?.content)
        assertEquals("worker", taskJson["executor"]?.jsonPrimitive?.content)
        assertEquals("planned", taskJson["status"]?.jsonPrimitive?.content)
        assertEquals(listOf("the tests pass"), taskJson["acceptance"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    private fun fileJson(draft: com.inscopelabs.buildengine.domain.model.PlanDraft, path: String): JsonObject {
        val file = PlanDraftAllocator.materialize(draft).first { it.path == path }
        return json.parseToJsonElement(file.content).jsonObject
    }
}
