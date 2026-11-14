package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.DoctorReport
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * Runs gateway diagnostics (`GET /doctor`, §3.14; T3-405).
 * Read-only check requiring no write capability.
 */
class GetDoctorReportUseCase(private val planRepository: PlanRepository) {

    suspend operator fun invoke(): AppResult<DoctorReport> = planRepository.doctor()
}
