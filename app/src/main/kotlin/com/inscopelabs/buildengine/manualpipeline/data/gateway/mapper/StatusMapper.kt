package com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper

import com.inscopelabs.buildengine.core.network.dto.PlanStatusDto
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanStatusSnapshot

object StatusMapper {

    fun toSnapshot(dto: PlanStatusDto): PlanStatusSnapshot = PlanStatusSnapshot(
        planId = dto.id,
        status = dto.status,
        halted = dto.halted,
        openBranches = dto.openBranches,
        blockedTasks = dto.blockedTasks,
        currentMilestone = dto.currentMilestone,
    )
}
