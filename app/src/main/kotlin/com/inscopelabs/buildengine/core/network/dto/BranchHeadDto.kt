package com.inscopelabs.buildengine.core.network.dto

import kotlinx.serialization.Serializable

/**
 * The current remote head of a branch.
 *
 * docs/gateway-contract.md §3.7 — `GET /branches/{name}/head`.
 *
 * This is the head-drift precondition. `VerifyBranchHeadUseCase` reads it before a
 * capability is minted, so that a capability is never minted against a SHA the
 * branch has already left behind (docs/threat-model.md, V7).
 */
@Serializable
data class BranchHeadDto(
    val branch: String = "",
    val headSha: String = "",
)
