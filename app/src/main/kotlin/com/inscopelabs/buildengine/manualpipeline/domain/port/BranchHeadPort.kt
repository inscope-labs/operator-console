package com.inscopelabs.buildengine.manualpipeline.domain.port

/**
 * Port for Gateway A branch head read: `GET /branches/{name}/head`.
 */
interface BranchHeadPort {
    suspend fun fetchLiveHead(branch: String): Result<BranchHeadInfo>
}

data class BranchHeadInfo(
    val branch: String,
    val headSha: String,
)
