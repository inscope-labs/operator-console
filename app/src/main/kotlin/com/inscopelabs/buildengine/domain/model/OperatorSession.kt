package com.inscopelabs.buildengine.domain.model

/**
 * The live operator session.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §9: held in memory only. There is no durable session token.
 */
data class OperatorSession(
    val operatorId: String,
    val startedAtEpochSeconds: Long,
)
