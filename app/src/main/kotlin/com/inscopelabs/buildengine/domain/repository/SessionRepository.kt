package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.domain.model.OperatorSession

/**
 * The operator session.
 *
 * Design review §9: the session is in-memory only and is convenience, not the
 * security control. The capability TTL and the gateway's credential TTL are the
 * effective bounds. Nothing durable may be written here.
 */
interface SessionRepository {

    /**
     * Opens a session, or returns the one already open.
     *
     * Idempotent, so a configuration change cannot rotate the session's identity
     * underneath the operator.
     */
    suspend fun start(): OperatorSession

    /** Ends the session and drops all of its state. */
    suspend fun end()

    /** The live session, or `null` when no session is open. */
    fun current(): OperatorSession?
}
