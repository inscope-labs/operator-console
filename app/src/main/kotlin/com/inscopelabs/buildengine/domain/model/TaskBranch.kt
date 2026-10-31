package com.inscopelabs.buildengine.domain.model

/**
 * A minted task branch.
 *
 * Design review §3: the minted branch is the only ref an operator may ever write.
 *
 * `guardsPassed` is nullable, and that is the whole point of it. The console
 * evaluates no guards — the gateway does, on the write — and no read endpoint
 * reports their outcome, so the console does not know. `null` says "not known";
 * `false` would say "the guards did not pass", which is a claim about the gateway's
 * verdict that the console has no basis for. The default is the honest answer.
 */
data class TaskBranch(
    val name: String,
    val headSha: String,
    /** Whether the gateway's guards passed. `null` when the console has not been told. */
    val guardsPassed: Boolean? = null,
)
