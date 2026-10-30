package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.domain.model.OperatorSession

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
interface SessionRepository {

    suspend fun start(): OperatorSession

    suspend fun end()

    fun current(): OperatorSession?
}
