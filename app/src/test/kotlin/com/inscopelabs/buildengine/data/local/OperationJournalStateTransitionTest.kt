package com.inscopelabs.buildengine.data.local

import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.testutil.InMemoryOperationJournalDao
import com.inscopelabs.buildengine.testutil.RecordingFakeJournalCipher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive state-transition matrix and DAO-boundary transaction/CAS tests for PR3-011 (`T3-202`, `I-03`).
 */
class OperationJournalStateTransitionTest {

    private val expectedAllowedTransitions: Map<OperationJournalState, Set<OperationJournalState>> = mapOf(
        OperationJournalState.INTENT_RECORDED to setOf(
            OperationJournalState.SENT,
            OperationJournalState.RECOVERY_REQUIRED,
        ),
        OperationJournalState.SENT to setOf(
            OperationJournalState.ACKNOWLEDGED,
            OperationJournalState.UNCERTAIN,
            OperationJournalState.TERMINAL_SUCCEEDED,
            OperationJournalState.TERMINAL_REFUSED,
            OperationJournalState.TERMINAL_CONFLICT,
            OperationJournalState.RECOVERY_REQUIRED,
        ),
        OperationJournalState.ACKNOWLEDGED to setOf(
            OperationJournalState.UNCERTAIN,
            OperationJournalState.TERMINAL_SUCCEEDED,
            OperationJournalState.TERMINAL_REFUSED,
            OperationJournalState.TERMINAL_CONFLICT,
            OperationJournalState.RECOVERY_REQUIRED,
        ),
        OperationJournalState.UNCERTAIN to setOf(
            OperationJournalState.UNCERTAIN,
            OperationJournalState.TERMINAL_SUCCEEDED,
            OperationJournalState.TERMINAL_REFUSED,
            OperationJournalState.TERMINAL_CONFLICT,
            OperationJournalState.RECOVERY_REQUIRED,
        ),
        OperationJournalState.TERMINAL_SUCCEEDED to emptySet(),
        OperationJournalState.TERMINAL_REFUSED to emptySet(),
        OperationJournalState.TERMINAL_CONFLICT to emptySet(),
        OperationJournalState.RECOVERY_REQUIRED to emptySet(),
    )

    @Test
    fun `exhaustive 8x8 state matrix enforces forward-only transitions and blocks backwards or skipped states`() {
        for (from in OperationJournalState.entries) {
            val expectedAllowed = expectedAllowedTransitions.getValue(from)
            assertEquals("Allowed transition set mismatch for $from", expectedAllowed, from.allowedNextStates())
            for (to in OperationJournalState.entries) {
                if (to in expectedAllowed) {
                    assertTrue("Expected $from -> $to to be valid", from.canTransitionTo(to))
                    OperationJournalState.requireValidTransition(from, to)
                } else {
                    assertFalse("Expected $from -> $to to be rejected", from.canTransitionTo(to))
                    val ex = runCatching { OperationJournalState.requireValidTransition(from, to) }.exceptionOrNull()
                    assertTrue(
                        "Expected IllegalArgumentException for $from -> $to, got $ex",
                        ex is IllegalArgumentException,
                    )
                }
            }
        }
    }

    @Test
    fun `DAO transitionState rejects backwards and skipped state jumps without mutating stored row`() = runTest {
        val dao = InMemoryOperationJournalDao()
        val cipher = RecordingFakeJournalCipher()
        val key = "0123456789abcdef0123456789abcdef"
        val initial = OperationJournalCodec.encryptNewEntity(
            idempotencyKey = key,
            intent = sampleIntent(),
            cipher = cipher,
            nowEpochMs = 1_000L,
        )
        dao.commitIntentIfNoActive(initial, listOf(OperationJournalState.INTENT_RECORDED))

        // Skipping SENT directly from INTENT_RECORDED must be rejected.
        val skippedTargets = listOf(
            OperationJournalState.ACKNOWLEDGED,
            OperationJournalState.UNCERTAIN,
            OperationJournalState.TERMINAL_SUCCEEDED,
            OperationJournalState.TERMINAL_REFUSED,
            OperationJournalState.TERMINAL_CONFLICT,
        )
        for (target in skippedTargets) {
            val error = runCatching {
                dao.transitionState(key, OperationJournalState.INTENT_RECORDED, target, 1_100L)
            }.exceptionOrNull()
            assertTrue("Skipping SENT ($target) must throw", error is IllegalArgumentException)
            assertEquals(OperationJournalState.INTENT_RECORDED, dao.findByKey(key)?.state)
        }

        // Advance legitimately: INTENT_RECORDED -> SENT -> ACKNOWLEDGED -> TERMINAL_SUCCEEDED.
        assertTrue(dao.transitionState(key, OperationJournalState.INTENT_RECORDED, OperationJournalState.SENT, 1_200L))
        assertTrue(
            runCatching {
                dao.transitionState(key, OperationJournalState.SENT, OperationJournalState.INTENT_RECORDED, 1_250L)
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertEquals(OperationJournalState.SENT, dao.findByKey(key)?.state)

        assertTrue(dao.transitionState(key, OperationJournalState.SENT, OperationJournalState.ACKNOWLEDGED, 1_300L))
        assertTrue(
            runCatching {
                dao.transitionState(key, OperationJournalState.ACKNOWLEDGED, OperationJournalState.SENT, 1_350L)
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            dao.transitionState(
                key,
                OperationJournalState.ACKNOWLEDGED,
                OperationJournalState.TERMINAL_SUCCEEDED,
                1_400L,
            ),
        )

        // Terminal states cannot move backwards or sideways.
        for (target in OperationJournalState.entries) {
            val error = runCatching {
                dao.transitionState(key, OperationJournalState.TERMINAL_SUCCEEDED, target, 1_500L)
            }.exceptionOrNull()
            assertTrue("Transition out of TERMINAL_SUCCEEDED to $target must fail", error is IllegalArgumentException)
        }
        assertEquals(OperationJournalState.TERMINAL_SUCCEEDED, dao.findByKey(key)?.state)
    }

    @Test
    fun `DAO commitIntentIfNoActive rejects non-INTENT_RECORDED initial state and deduplicates active fingerprint`() =
        runTest {
            val dao = InMemoryOperationJournalDao()
            val cipher = RecordingFakeJournalCipher()
            val intent = sampleIntent()
            val entity1 = OperationJournalCodec.encryptNewEntity(
                idempotencyKey = "0123456789abcdef0123456789abcdef",
                intent = intent,
                cipher = cipher,
                nowEpochMs = 1_000L,
            )

            // Reject inserting a row already pretending to be SENT or TERMINAL_SUCCEEDED.
            for (badState in OperationJournalState.entries.filter { it != OperationJournalState.INTENT_RECORDED }) {
                val err = runCatching {
                    dao.commitIntentIfNoActive(entity1.copy(state = badState), OperationJournalState.entries)
                }.exceptionOrNull()
                assertTrue("Initial insert in $badState must fail", err is IllegalArgumentException)
            }
            assertEquals(0, dao.getAll().size)

            // Concurrent commits for the same active fingerprint produce a single row and a single winning CAS to SENT.
            val gate = CompletableDeferred<Unit>()
            val results = (1..8).map { idx ->
                async {
                    val key = "%032x".format(idx)
                    val candidate = OperationJournalCodec.encryptNewEntity(
                        idempotencyKey = key,
                        intent = intent,
                        cipher = cipher,
                        nowEpochMs = 2_000L + idx,
                    )
                    gate.await()
                    val active = dao.commitIntentIfNoActive(
                        candidate,
                        listOf(
                            OperationJournalState.INTENT_RECORDED,
                            OperationJournalState.SENT,
                            OperationJournalState.ACKNOWLEDGED,
                            OperationJournalState.UNCERTAIN,
                        ),
                    )
                    val casWon = dao.transitionState(
                        idempotencyKey = active.idempotencyKey,
                        expectedFromState = OperationJournalState.INTENT_RECORDED,
                        toState = OperationJournalState.SENT,
                        updatedAtEpochMs = 3_000L + idx,
                    )
                    active.idempotencyKey to casWon
                }
            }
            gate.complete(Unit)
            val resolved = results.awaitAll()

            assertEquals(1, dao.getAll().size)
            assertEquals(1, resolved.map { it.first }.distinct().size)
            assertEquals(1, resolved.count { it.second })
            assertEquals(OperationJournalState.SENT, dao.getAll().single().state)
        }

    private fun sampleIntent(): OperationIntent = OperationIntent(
        operation = OperationType.PUBLISH,
        plan = "atlas",
        task = "10008",
        branch = "task-10008-refactor-parser",
        expectedHead = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
    )
}
