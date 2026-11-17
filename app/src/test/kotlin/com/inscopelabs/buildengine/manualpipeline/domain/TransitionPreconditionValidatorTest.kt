package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.Fixtures
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.domain.validation.TransitionPreconditionValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Precondition tests for the manual pipeline's transition gate.
 *
 * This validator is the single choke point the UI (`ActIdleViewModel`) and the dispatch path
 * (`RequestTransitionUseCase`) both consult, so a refusal here means nothing was offered and
 * nothing was sent.
 */
class TransitionPreconditionValidatorTest {

    private val validator = TransitionPreconditionValidator()

    @Test
    fun `a ready task with an order id may start in progress`() {
        val result = validator.validate(Fixtures.task(status = TaskStatus.READY), TransitionKind.SET_IN_PROGRESS)
        assertTrue(result.permitted)
    }

    @Test
    fun `a task without a work-order id is refused`() {
        val result = validator.validate(
            Fixtures.task(orderId = "", status = TaskStatus.READY),
            TransitionKind.SET_IN_PROGRESS,
        )
        assertFalse(result.permitted)
        assertTrue(result.reason.contains("order", ignoreCase = true))
    }

    @Test
    fun `a task without a full 40-character head SHA is refused`() {
        val result = validator.validate(
            Fixtures.task(headSha = "abc123", status = TaskStatus.READY),
            TransitionKind.SET_IN_PROGRESS,
        )
        assertFalse(result.permitted)
        assertTrue(result.reason.contains("40", ignoreCase = true))
    }

    @Test
    fun `a halted plan is refused`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.READY, isPlanHalted = true),
            TransitionKind.SET_IN_PROGRESS,
        )
        assertFalse(result.permitted)
        assertTrue(result.reason.contains("halt", ignoreCase = true))
    }

    @Test
    fun `an unrecognized status fails closed`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.UNKNOWN),
            TransitionKind.SET_IN_PROGRESS,
        )
        assertFalse(result.permitted)
        assertTrue(result.reason.contains("unrecognized", ignoreCase = true))
    }

    @Test
    fun `a gateway-blocked task is refused`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.READY, isBlockedByGateway = true),
            TransitionKind.SET_IN_PROGRESS,
        )
        assertFalse(result.permitted)
        assertTrue(result.reason.contains("blocked", ignoreCase = true))
    }

    @Test
    fun `a task already in progress cannot be started again`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.IN_PROGRESS),
            TransitionKind.SET_IN_PROGRESS,
        )
        assertFalse(result.permitted)
    }

    @Test
    fun `a completed task cannot move back to in progress`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.COMPLETED),
            TransitionKind.SET_IN_PROGRESS,
        )
        assertFalse(result.permitted)
    }

    // -- the SET_DONE hold (BE-MP-12) --

    @Test
    fun `SET_DONE is held back while no seal route exists for a console upload`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.IN_PROGRESS),
            TransitionKind.SET_DONE,
        )
        assertFalse(result.permitted)
        assertTrue(result.reason.contains("SEAL", ignoreCase = true))
    }

    @Test
    fun `SET_DONE is held back even from a ready task`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.READY),
            TransitionKind.SET_DONE,
        )
        assertFalse(result.permitted)
    }

    @Test
    fun `the SET_DONE hold names the missing contract work`() {
        val result = validator.validate(
            Fixtures.task(status = TaskStatus.IN_PROGRESS),
            TransitionKind.SET_DONE,
        )
        assertTrue(result.reason.contains("BE-MP-12"))
    }
}
