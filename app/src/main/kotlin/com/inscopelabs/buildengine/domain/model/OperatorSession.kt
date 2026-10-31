package com.inscopelabs.buildengine.domain.model

/**
 * The live operator session.
 *
 * Design review §9: **held in memory only.** There is no durable session token.
 *
 * The threat model lists *"persisted session tokens"* among the things this console
 * does not do, and the reason is that a persisted session token is a bearer token
 * with a long life and no scope. The console already holds one credential — the
 * gateway-scoped operator credential — and the boundary of that credential is
 * precisely understood. A second long-lived secret with a vaguer meaning would be
 * one more thing to get wrong, for no gain.
 *
 * What the session is for is narrower than it sounds: it records that an operator is
 * present, and it gives the console a place to hang "the operator has ended the
 * session" so the rest of the app can react. It is **convenience, not the security
 * control**. The effective bounds are the capability's five-minute TTL and the
 * gateway's credential TTL (design review §9), and neither of them is extended by
 * this object.
 *
 * The attribution of an action is the gateway's job, done from the capability it
 * verifies — not from this identifier.
 */
data class OperatorSession(
    /** A local identifier for the operator of this session. Never leaves the device. */
    val operatorId: String,
    /** When the session opened, in Unix epoch seconds. */
    val startedAtEpochSeconds: Long,
)
