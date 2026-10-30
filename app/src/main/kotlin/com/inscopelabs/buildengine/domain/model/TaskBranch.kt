package com.inscopelabs.buildengine.domain.model

/**
 * A minted task branch.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §3: the minted branch is the only ref an operator may ever write.
 */
data class TaskBranch(
    val name: String,
    val headSha: String,
    val guardsPassed: Boolean = false,
)
