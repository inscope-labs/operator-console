package com.inscopelabs.buildengine.manualpipeline.domain

import com.inscopelabs.buildengine.manualpipeline.FakeJournalPort
import com.inscopelabs.buildengine.manualpipeline.FakeOperationLookupPort
import com.inscopelabs.buildengine.manualpipeline.Fixtures
import com.inscopelabs.buildengine.manualpipeline.FixedTestClock
import com.inscopelabs.buildengine.manualpipeline.LookupOnlyExecutorPort
import com.inscopelabs.buildengine.manualpipeline.RecordingOperatorActionStore
import com.inscopelabs.buildengine.manualpipeline.StrictTestRedactor
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationRecord
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryState
import com.inscopelabs.buildengine.manualpipeline.domain.model.RecoveryStatus
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.RecordOperatorActionUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ReconcileOperationUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recovery tests for the manual pipeline's lookup-only reconciliation (MP-010).
 *
 * An uncertain write is never resent. Resolution is by `GET /operations/{id}`; if the
 * operation is still unresolved, the module surfaces `RecoveryRequired` rather than
 * guessing (I-05).
 */
class ReconcileOperationUseCaseTest {

    private val actionStore = RecordingOperatorActionStore()

    private fun useCase(
        executor: LookupOnlyExecutorPort = LookupOnlyExecutorPort(),
        lookup: FakeOperationLookupPort = FakeOperationLookupPort(),
        journal: FakeJournalPort = FakeJournalPort(),
    ): ReconcileOperationUseCase = ReconcileOperationUseCase(
        journalPort = journal,
        operationLookupPort = lookup,
        executorPort = executor,
        recordOperatorActionUseCase = RecordOperatorActionUseCase(
            store = actionStore,
            redactor = StrictTestRedactor(),
            clock = FixedTestClock(),
        ),
    )

    private fun record(
        key: String = "c".repeat(32),
        state: String = "UNCERTAIN",
    ): OperationRecord = OperationRecord(
        idempotencyKey = key,
        operationName = "UPLOAD",
        planId = Fixtures.PLAN_ID,
        taskId = Fixtures.TASK_ID,
        branch = Fixtures.BRANCH,
        expectedHead = Fixtures.HEAD_SHA,
        state = state,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 2L,
    )

    @Test
    fun `a completed operation resolves as succeeded`() = runTest {
        val executor = LookupOnlyExecutorPort(
            OperationOutcome.Succeeded(auditRef = "audit-9", headSha = Fixtures.HEAD_SHA),
        )
        val status = useCase(executor = executor).reconcileByKey("c".repeat(32))

        assertEquals(RecoveryState.RESOLVED_SUCCEEDED, status.state)
        assertEquals("audit-9", status.auditRef)
    }

    @Test
    fun `a refused operation resolves as refused and keeps the gateway code`() = runTest {
        val executor = LookupOnlyExecutorPort(
            OperationOutcome.Refused(code = "SEAL_NOT_ATTESTED", message = "no seal"),
        )
        val status = useCase(executor = executor).reconcileByKey("c".repeat(32))

        assertEquals(RecoveryState.RESOLVED_REFUSED, status.state)
        assertEquals("SEAL_NOT_ATTESTED", status.errorCode)
    }

    @Test
    fun `a conflicting operation resolves as a conflict`() = runTest {
        val executor = LookupOnlyExecutorPort(
            OperationOutcome.Conflict(code = "HEAD_MOVED", message = "branch advanced"),
        )
        val status = useCase(executor = executor).reconcileByKey("c".repeat(32))

        assertEquals(RecoveryState.RESOLVED_CONFLICT, status.state)
    }

    @Test
    fun `an unknown operation requires manual review and is never resent`() = runTest {
        val executor = LookupOnlyExecutorPort(
            OperationOutcome.RecoveryRequired(
                idempotencyKey = "c".repeat(32),
                code = "OPERATION_NOT_FOUND",
            ),
        )
        val status = useCase(executor = executor).reconcileByKey("c".repeat(32))

        assertEquals(RecoveryState.MANUAL_REVIEW_REQUIRED, status.state)
        assertEquals("OPERATION_NOT_FOUND", status.errorCode)
        assertEquals(0, executor.transitionWrites)
        assertEquals(0, executor.uploadWrites)
    }

    @Test
    fun `a still pending operation falls back to the read-only lookup port`() = runTest {
        val executor = LookupOnlyExecutorPort(
            OperationOutcome.Timeout(idempotencyKey = "c".repeat(32)),
        )
        val lookup = FakeOperationLookupPort(
            Result.failure(IllegalStateException("gateway unreachable")),
        )
        val status = useCase(executor = executor, lookup = lookup).reconcileByKey("c".repeat(32))

        assertEquals(RecoveryState.PENDING_RETRY, status.state)
        assertEquals(1, lookup.calls)
    }

    @Test
    fun `the read-only lookup port result is used when the gateway has one`() = runTest {
        val executor = LookupOnlyExecutorPort(
            OperationOutcome.UncertainCompletion(idempotencyKey = "c".repeat(32)),
        )
        val lookup = FakeOperationLookupPort(
            Result.success(
                RecoveryStatus(
                    idempotencyKey = "c".repeat(32),
                    state = RecoveryState.RESOLVED_SUCCEEDED,
                    gatewayOperationStatus = "completed",
                    auditRef = "audit-3",
                ),
            ),
        )
        val status = useCase(executor = executor, lookup = lookup).reconcileByKey("c".repeat(32))

        assertEquals(RecoveryState.RESOLVED_SUCCEEDED, status.state)
        assertEquals("audit-3", status.auditRef)
    }

    @Test
    fun `reconciliation never writes`() = runTest {
        val executor = LookupOnlyExecutorPort()
        useCase(executor = executor).reconcileByKey("c".repeat(32))

        assertEquals(0, executor.transitionWrites)
        assertEquals(0, executor.uploadWrites)
        assertEquals(1, executor.lookupCalls)
    }

    @Test
    fun `the recovery queue lists only uncertain or in-flight records`() = runTest {
        val journal = FakeJournalPort(
            listOf(
                record(key = "1".repeat(32), state = "UNCERTAIN"),
                record(key = "2".repeat(32), state = "IN_FLIGHT"),
                record(key = "3".repeat(32), state = "COMPLETED"),
            ),
        )
        val queue = useCase(journal = journal).loadRecoveryQueue()

        assertEquals(2, queue.size)
        assertTrue(queue.none { it.state == "COMPLETED" })
    }

    @Test
    fun `a reconciled operation is recorded as an operator action`() = runTest {
        val executor = LookupOnlyExecutorPort(
            OperationOutcome.Succeeded(auditRef = "audit-9", headSha = Fixtures.HEAD_SHA),
        )
        val journal = FakeJournalPort(listOf(record()))
        useCase(executor = executor, journal = journal).reconcileByKey("c".repeat(32))

        assertEquals(1, actionStore.recorded.size)
        val action = actionStore.recorded.single()
        assertEquals("LOOKUP_RECOVERY", action.actionName)
        assertEquals(RecoveryState.RESOLVED_SUCCEEDED.name, action.outcomeLabel)
        assertEquals("c".repeat(32), action.idempotencyKey)
    }
}
