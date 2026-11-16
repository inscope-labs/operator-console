package com.inscopelabs.buildengine.domain.authoring

import com.inscopelabs.buildengine.domain.model.PlanCoverageExemption
import com.inscopelabs.buildengine.domain.model.PlanContextRef
import com.inscopelabs.buildengine.domain.model.PlanContextRole
import com.inscopelabs.buildengine.domain.model.PlanContextSource
import com.inscopelabs.buildengine.domain.model.PlanDependency
import com.inscopelabs.buildengine.domain.model.PlanDependencyType
import com.inscopelabs.buildengine.domain.validation.PlanDraftValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation tests for the canonical Build Engine plan rules.
 *
 * These are the rules the canonical plan tooling applies to a plan directory: identifier shapes,
 * stage ordering, path safety, dependency resolution and the schema's own required fields.
 */
class PlanDraftValidatorTest {

    private val validator = PlanDraftValidator()

    private fun errors(draft: com.inscopelabs.buildengine.domain.model.PlanDraft) =
        validator.validate(draft).errors

    private fun fields(draft: com.inscopelabs.buildengine.domain.model.PlanDraft) =
        errors(draft).map { it.field }

    private fun messages(draft: com.inscopelabs.buildengine.domain.model.PlanDraft) =
        errors(draft).map { it.message }

    @Test
    fun `a complete plan passes the canonical rules`() {
        val report = validator.validate(PlanDraftFixtures.draft())

        assertTrue(report.errors.isEmpty())
        assertTrue(report.isValid)
    }

    @Test
    fun `the canonical validator is reported as unavailable`() {
        val report = validator.validate(PlanDraftFixtures.draft())

        assertFalse(report.canonicalServerValidationAvailable)
        assertTrue(report.canonicalServerValidationNote.contains("unavailable"))
    }

    // -- plan.json --

    @Test
    fun `an empty title is an error`() {
        val draft = PlanDraftFixtures.draft(title = "   ")

        assertTrue(fields(draft).contains("plan.title"))
    }

    @Test
    fun `a plan id with illegal characters is an error`() {
        val draft = PlanDraftFixtures.draft(planId = "My Plan")

        assertTrue(fields(draft).contains("plan.plan_id"))
    }

    @Test
    fun `an unreserved plan number is an error and says the identifiers are provisional`() {
        val draft = PlanDraftFixtures.draft(planNumber = "")

        val issue = errors(draft).first { it.field == "plan.plan_number" }

        assertTrue(issue.message.contains("not reserved"))
        assertTrue(issue.message.contains("provisional"))
    }

    @Test
    fun `a plan number that is not five digits is an error`() {
        val draft = PlanDraftFixtures.draft(planNumber = "123")

        assertTrue(fields(draft).contains("plan.plan_number"))
    }

    @Test
    fun `a deliverable pattern that does not compile is an error`() {
        val draft = PlanDraftFixtures.draft(deliverablePatterns = listOf("[unclosed"))

        assertTrue(fields(draft).any { it.startsWith("plan.deliverable_patterns") })
    }

    // -- stages.json --

    @Test
    fun `a plan with no phase is an error`() {
        val draft = PlanDraftFixtures.draft(phases = emptyList())

        assertTrue(fields(draft).contains("phases"))
    }

    @Test
    fun `a stage whose phase is not declared is an error`() {
        val draft = PlanDraftFixtures.draft(
            phases = listOf(PlanDraftFixtures.phase("1")),
            stages = listOf(PlanDraftFixtures.stage("2-1")),
        )

        assertTrue(fields(draft).any { it.startsWith("stages[0].id") })
    }

    @Test
    fun `stages that are not in ascending order are an error`() {
        val draft = PlanDraftFixtures.draft(
            phases = listOf(PlanDraftFixtures.phase("1"), PlanDraftFixtures.phase("2")),
            stages = listOf(PlanDraftFixtures.stage("2-1"), PlanDraftFixtures.stage("1-1")),
        )

        assertTrue(fields(draft).contains("stages"))
    }

    @Test
    fun `duplicate phase ids are an error`() {
        val draft = PlanDraftFixtures.draft(
            phases = listOf(PlanDraftFixtures.phase("1"), PlanDraftFixtures.phase("1", "Another")),
        )

        assertTrue(fields(draft).any { it.startsWith("phases[1].id") })
    }

    // -- tasks --

    @Test
    fun `a task with no acceptance criterion is an error`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(PlanDraftFixtures.task(acceptance = emptyList())),
        )

        assertTrue(fields(draft).any { it.endsWith(".acceptance") })
    }

    @Test
    fun `a task on an unknown stage is an error`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(PlanDraftFixtures.task(stageId = "9-9")),
        )

        assertTrue(fields(draft).any { it.endsWith(".stage") })
    }

    @Test
    fun `an unsafe deliverable path is an error`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(PlanDraftFixtures.task(deliverables = listOf("../outside.txt"))),
        )

        assertTrue(messages(draft).any { it.contains("unsafe deliverable path") })
    }

    @Test
    fun `an absolute deliverable path is an error`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(PlanDraftFixtures.task(deliverables = listOf("/etc/passwd"))),
        )

        assertTrue(messages(draft).any { it.contains("unsafe deliverable path") })
    }

    @Test
    fun `a deliverable outside the declared roots is an error`() {
        val draft = PlanDraftFixtures.draft(
            deliverableRoots = listOf("src/"),
            tasks = listOf(PlanDraftFixtures.task(deliverables = listOf("docs/readme.md"))),
        )

        assertTrue(messages(draft).any { it.contains("outside deliverable_roots") })
    }

    @Test
    fun `a deliverable that matches no declared pattern is an error`() {
        val draft = PlanDraftFixtures.draft(
            deliverablePatterns = listOf("^src/"),
            tasks = listOf(PlanDraftFixtures.task(deliverables = listOf("docs/readme.md"))),
        )

        assertTrue(messages(draft).any { it.contains("does not match deliverable_patterns") })
    }

    @Test
    fun `an unsafe context path is an error`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(
                PlanDraftFixtures.task(
                    context = listOf(
                        PlanContextRef(
                            source = PlanContextSource.TARGET,
                            path = "../outside",
                            role = PlanContextRole.READ,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(messages(draft).any { it.contains("unsafe context path") })
    }

    @Test
    fun `a dependency on a task that does not exist is an error`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(
                PlanDraftFixtures.task(
                    localId = "a",
                    blockedBy = listOf(PlanDependency(type = PlanDependencyType.TASK, ref = "missing")),
                ),
            ),
        )

        assertTrue(messages(draft).any { it.contains("blocked_by unknown task") })
    }

    @Test
    fun `a dependency cycle is an error`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(
                PlanDraftFixtures.task(
                    localId = "a",
                    ordinal = 0,
                    blockedBy = listOf(PlanDependency(type = PlanDependencyType.TASK, ref = "b")),
                ),
                PlanDraftFixtures.task(
                    localId = "b",
                    ordinal = 1,
                    blockedBy = listOf(PlanDependency(type = PlanDependencyType.TASK, ref = "a")),
                ),
            ),
        )

        assertTrue(errors(draft).any { it.message.contains("dependency cycle") })
    }

    @Test
    fun `a decision dependency is a warning because the device cannot resolve it`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(
                PlanDraftFixtures.task(
                    blockedBy = listOf(PlanDependency(type = PlanDependencyType.DECISION, ref = "D-1")),
                ),
            ),
        )

        val report = validator.validate(draft)

        assertTrue(report.errors.isEmpty())
        assertTrue(report.warnings.any { it.message.contains("decision dependencies") })
    }

    @Test
    fun `a task with no deliverables is a warning`() {
        val draft = PlanDraftFixtures.draft(
            tasks = listOf(PlanDraftFixtures.task(deliverables = emptyList())),
        )

        val report = validator.validate(draft)

        assertTrue(report.errors.isEmpty())
        assertTrue(report.warnings.any { it.message.contains("no deliverables") })
    }

    // -- coverage_exempt.json --

    @Test
    fun `a coverage exemption without a reason is an error`() {
        val draft = PlanDraftFixtures.draft(
            coverageExemptions = listOf(PlanCoverageExemption(id = "SPEC-1", reason = "")),
        )

        assertTrue(fields(draft).contains("coverage[0].reason"))
    }

    @Test
    fun `issues are reported against the field they belong to`() {
        val draft = PlanDraftFixtures.draft(title = "")

        val issue = errors(draft).first { it.field == "plan.title" }

        assertEquals("plan.title", issue.field)
    }
}
