package com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper

import com.inscopelabs.buildengine.core.network.dto.BranchHeadDto
import com.inscopelabs.buildengine.manualpipeline.domain.port.BranchHeadInfo

object HeadMapper {

    fun toBranchHeadInfo(dto: BranchHeadDto): BranchHeadInfo = BranchHeadInfo(
        branch = dto.branch,
        headSha = dto.headSha.trim(),
    )
}
