package com.inscopelabs.buildengine.manualpipeline.data.gateway

import com.inscopelabs.buildengine.data.remote.GatewayApi
import com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper.HeadMapper
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadInfo
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadPort
import javax.inject.Inject

/**
 * Gateway A live branch head reader (`GET /branches/{name}/head`).
 */
class GatewayABranchHeadAdapter @Inject constructor(
    private val gatewayApi: GatewayApi,
) : BranchHeadPort {

    override suspend fun fetchLiveHead(branch: String): Result<BranchHeadInfo> = runCatching {
        HeadMapper.toBranchHeadInfo(gatewayApi.branchHead(branch))
    }
}
