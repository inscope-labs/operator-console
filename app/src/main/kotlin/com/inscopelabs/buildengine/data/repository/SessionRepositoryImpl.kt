package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.crypto.SecureRandomSource
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.domain.model.OperatorSession
import com.inscopelabs.buildengine.domain.repository.SessionRepository

/**
 * The operator session, in memory and nowhere else.
 *
 * Design review §9: the session is in-memory only and is convenience, not the
 * security control. The capability TTL and the gateway's credential TTL are the
 * effective bounds. Nothing durable may be written here.
 *
 * There is no persistence layer behind this class on purpose, and there is no
 * interface for one to be added behind later: a session that survives the process
 * would be a bearer token with an unbounded life, which is exactly what the threat
 * model refuses.
 *
 * ### The identifier
 *
 * [OperatorSession.operatorId] is drawn from the platform CSPRNG when the session
 * opens. It is a local label for "who is at the console right now", and it is not
 * the operator's identity at the gateway — the gateway attributes every action from
 * the capability it verifies, and the console's own identifier is not part of any
 * request it makes.
 */
class SessionRepositoryImpl(
    private val clock: Clock,
    private val randomSource: SecureRandomSource,
) : SessionRepository {

    @Volatile
    private var session: OperatorSession? = null

    /**
     * Opens a session, or returns the one already open.
     *
     * Idempotent on purpose. A configuration change — a rotation, a theme switch —
     * must not silently rotate the session's identity, and an operator who has
     * explicitly ended the session must not have it reopened by the Activity
     * starting again.
     */
    override suspend fun start(): OperatorSession = openSession()

    /** Ends the session and drops all of its state. */
    override suspend fun end() {
        synchronized(this) { session = null }
    }

    /** The live session, or `null` when no session is open. */
    override fun current(): OperatorSession? = session

    /**
     * Opens the session under a lock.
     *
     * The lock is on the instance rather than `synchronized` on the method, because
     * the method is `suspend`: holding a monitor across a suspension point is a
     * deadlock waiting for a slow dispatcher. There is no suspension point inside
     * here, so the lock is held for a handful of instructions and never longer.
     */
    private fun openSession(): OperatorSession = synchronized(this) {
        session ?: OperatorSession(
            operatorId = randomSource.nextNonce(),
            startedAtEpochSeconds = clock.nowEpochSeconds(),
        ).also { session = it }
    }
}
