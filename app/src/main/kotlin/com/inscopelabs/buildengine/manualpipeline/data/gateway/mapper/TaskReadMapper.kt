package com.inscopelabs.buildengine.manualpipeline.data.gateway.mapper

import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.TaskStatus
import com.inscopelabs.buildengine.manualpipeline.domain.port.PlanSummary

object TaskReadMapper {

    fun toDomain(dto: TaskSummaryDto): ManualTask = ManualTask(
        id = dto.id,
        planId = dto.planId,
        title = dto.title,
        orderId = dto.order,
        stage = null,
        status = TaskStatus.fromWire(dto.status),
        branch = dto.branch,
        headSha = dto.headSha,
    )

    fun toPlanSummary(dto: PlanSummaryDto): PlanSummary = PlanSummary(
        id = dto.id,
        title = dto.title,
        taskCount = dto.taskCount,
        completedTaskCount = dto.completedTaskCount,
        status = dto.status,
    )
}
