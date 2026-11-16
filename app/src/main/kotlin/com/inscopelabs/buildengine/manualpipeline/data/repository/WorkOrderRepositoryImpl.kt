package com.inscopelabs.buildengine.manualpipeline.data.repository

import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.WorkOrder
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.InterpretWorkOrderUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides interpreted gateway work-order views without creating or mutating work orders locally
 * (ADR 0014 D5).
 */
@Singleton
class WorkOrderRepositoryImpl @Inject constructor(
    private val interpretWorkOrderUseCase: InterpretWorkOrderUseCase,
) {
    fun getWorkOrder(task: ManualTask): WorkOrder = interpretWorkOrderUseCase(task)
}
