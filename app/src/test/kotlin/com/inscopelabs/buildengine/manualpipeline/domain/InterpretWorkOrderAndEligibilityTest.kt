package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.Fixtures
import com.inscopelabs.buildengine.manualpipeline.domain.model.AcceptanceCriterion
import com.inscopelabs.buildengine.manualpipeline.domain.model.CompanionFile
import com.inscopelabs.buildengine.manualpipeline.domain.model.Deliverable
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.EvaluateEligibilityUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.InterpretWorkOrderUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.validation.EnvironmentIsolationValidator
import com.inscopelabs.buildengine.manualpipeline.domain.validation.HeadFreshnessValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Work-order interpretation and advisory eligibility tests.
 *
 * The evaluator is advisory only: it explains why a task is not eligible, but it never
 * grants permission. A local pass is not authorization (I-01, I-08).
 */
class InterpretWorkOrderAndEligibilityTest {

    private val interpretWorkOrder = InterpretWorkOrderUseCase()

    // -- InterpretWorkOrderUseCase --

    @Test
    fun `a work order is bound to the gateway-reported order id`() {
        val order = interpretWorkOrder(Fixtures.task(orderId = Fixtures.ORDER_ID))
        assertEquals(Fixtures.ORDER_ID, order.orderId)
        assertEquals(Fixtures.PLAN_ID, order.planId)
        assertEquals(Fixtures.TASK_ID, order.taskId)
    }

    @Test
    fun `no order identifier is invented when the gateway reports none`() {
        // Work-order creation stays deferred to engine tooling (ADR 0014 D5).
        val order = interpretWorkOrder(Fixtures.task(orderId = ""))
        assertEquals("", order.orderId)
        assertTrue(order.summary.contains("no work-order identifier", ignoreCase = true))
        assertTrue(order.summary.contains("disabled", ignoreCase = true))
    }

    @Test
    fun `default acceptance criteria are supplied when the gateway declares none`() {
        val order = interpretWorkOrder(Fixtures.task())
        assertTrue(order.acceptanceCriteria.isNotEmpty())
        assertTrue(order.acceptanceCriteria.any { it.id == "AC-HEAD" })
        assertTrue(order.acceptanceCriteria.all { it.description.isNotBlank() })
    }

    @Test
    fun `gateway-declared acceptance criteria are used verbatim`() {
        val declared = listOf(
            AcceptanceCriterion(id = "AC-1", description = "Run the conformance suite."),
        )
        val order = interpretWorkOrder(
            Fixtures.task(),
            acceptanceCriteria = declared,
        )
        assertEquals(declared, order.acceptanceCriteria)
    }

    @Test
    fun `declared deliverables and companions are carried through`() {
        val deliverables = listOf(Deliverable(relativePath = "submission/report.md"))
        val companions = listOf(CompanionFile(relativePath = "notes.md", purpose = "diff notes"))
        val order = interpretWorkOrder(
            Fixtures.task(),
            extraDeclaredDeliverables = deliverables,
            companionFiles = companions,
        )
        assertEquals(deliverables, order.deliverables)
        assertEquals(companions, order.companionFiles)
    }

    // -- EvaluateEligibilityUseCase --

    private val evaluateEligibility = EvaluateEligibilityUseCase(
        headFreshnessValidator = HeadFreshnessValidator(),
        environmentIsolationValidator = EnvironmentIsolationValidator(),
    )

    @Test
    fun `an eligible task passes every advisory check`() {
        val verdict = evaluateEligibility(
            task = Fixtures.task(status = TaskStatus.READY),
            authority = Fixtures.authority(),
            liveBranchHead = Fixtures.HEAD_SHA,
        )
        assertTrue(verdict.advisoryReady)
        assertTrue(verdict.checks.all { it.passed })
    }

    @Test
    fun `a task without a work-order id is not eligible`() {
        val verdict = evaluateEligibility(
            task = Fixtures.task(orderId = "", status = TaskStatus.READY),
            authority = Fixtures.authority(),
            liveBranchHead = Fixtures.HEAD_SHA,
        )
        assertFalse(verdict.advisoryReady)
        assertTrue(verdict.checks.first { !it.passed }.name == "Work-order binding")
    }

    @Test
    fun `a drifted head is reported as not eligible`() {
        val verdict = evaluateEligibility(
            task = Fixtures.task(status = TaskStatus.READY),
            authority = Fixtures.authority(),
            liveBranchHead = Fixtures.OTHER_HEAD_SHA,
        )
        assertFalse(verdict.advisoryReady)
        assertTrue(verdict.checks.first { !it.passed }.name == "Branch head freshness")
    }

    @Test
    fun `an unenrolled device is not eligible`() {
        val verdict = evaluateEligibility(
            task = Fixtures.task(status = TaskStatus.READY),
            authority = Fixtures.authority(enrolled = false),
            liveBranchHead = Fixtures.HEAD_SHA,
        )
        assertFalse(verdict.advisoryReady)
        assertTrue(verdict.checks.first { !it.passed }.name == "Device enrolment & credential")
    }

    @Test
    fun `an environment mismatch is not eligible`() {
        val verdict = evaluateEligibility(
            task = Fixtures.task(status = TaskStatus.READY),
            authority = Fixtures.authority(
                expectedEnvironment = Environment.STAGING,
                reportedEnvironment = Environment.PRODUCTION,
            ),
            liveBranchHead = Fixtures.HEAD_SHA,
        )
        assertFalse(verdict.advisoryReady)
        assertTrue(verdict.checks.first { !it.passed }.name == "Environment parity")
    }

    @Test
    fun `a halted plan is not eligible`() {
        val verdict = evaluateEligibility(
            task = Fixtures.task(status = TaskStatus.READY, isPlanHalted = true),
            authority = Fixtures.authority(),
            liveBranchHead = Fixtures.HEAD_SHA,
        )
        assertFalse(verdict.advisoryReady)
        assertTrue(verdict.checks.first { !it.passed }.name == "Halt & block state")
    }

    @Test
    fun `an advisory pass never claims the gateway will accept the write`() {
        val verdict = evaluateEligibility(
            task = Fixtures.task(status = TaskStatus.READY),
            authority = Fixtures.authority(),
            liveBranchHead = Fixtures.HEAD_SHA,
        )
        assertTrue(verdict.advisoryReady)
        assertTrue(
            verdict.summaryReason.contains("authoritative", ignoreCase = true),
        )
    }
}
