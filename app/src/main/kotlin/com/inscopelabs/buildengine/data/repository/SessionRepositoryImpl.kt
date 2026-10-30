package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.domain.model.OperatorSession
import com.inscopelabs.buildengine.domain.repository.SessionRepository

/**
 * The operator session.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §9: the session is in-memory only and is convenience, not the
 * security control. The capability TTL and the gateway's credential TTL are the
 * effective bounds. Nothing durable may be written here.
 */
class SessionRepositoryImpl : SessionRepository {

    override suspend fun start(): OperatorSession = TODO("SCAFFOLD: open a session.")

    override suspend fun end(): Unit = TODO("SCAFFOLD: drop all session state.")

    override fun current(): OperatorSession? = TODO("SCAFFOLD: return the live session.")
}
