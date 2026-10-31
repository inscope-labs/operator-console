package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.ErrorMapper

import com.inscopelabs.buildengine.core.network.dto.PlanStatusDto
import com.inscopelabs.buildengine.core.network.dto.PlanSummaryDto
import com.inscopelabs.buildengine.core.network.dto.PlanValidationDto
import com.inscopelabs.buildengine.core.network.dto.RenderCheckDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.repository.PlanRepository

/**
 * The plans of record, as the gateway reports them.
 *
 * This is the layer where the wire stops. The DTOs carry the gateway's vocabulary
 * — its status strings, its field names — and the domain model carries the
 * console's. The mapping is deliberately mechanical: the gateway's own words are
 * carried across rather than reinterpreted, because the console has no authority
 * to decide what a status means.
 */
class PlanRepositoryImpl(
    private val gateway: GatewayRepository,
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : PlanRepository {

    override suspend fun getPlans(): AppResult<List<Plan>> = callGateway(errorMapper) {
        gateway.listPlans().map { it.toPlan() }
    }

    /**
     * One plan, found in the plan list.
     *
     * The contract has no single-plan endpoint, so the list is the only place a
     * plan can come from. A plan that is not in it is reported as `not_found`:
     * inventing a blank plan would put an entry on the operator's screen that no
     * plan in the gateway resembles.
     */
    override suspend fun getPlan(planId: String): AppResult<Plan> = callGateway(errorMapper) {
        gateway.listPlans()
            .firstOrNull { it.id == planId }
            ?.toPlan()
            ?: throw NotFoundException("the gateway lists no plan '$planId'")
    }

    override suspend fun getStatus(planId: String): AppResult<PlanStatus> =
        callGateway(errorMapper) { gateway.planStatus(planId).toPlanStatus() }

    override suspend fun validate(planId: String): AppResult<PlanValidation> =
        callGateway(errorMapper) { gateway.validatePlan(planId).toPlanValidation() }

    override suspend fun renderCheck(planId: String): AppResult<RenderCheck> =
        callGateway(errorMapper) { gateway.renderCheck(planId).toRenderCheck() }
}

/**
 * A plan as the gateway reports it.
 */
private fun PlanSummaryDto.toPlan(): Plan = Plan(
    id = id,
    title = title,
    taskCount = taskCount,
    completedTaskCount = completedTaskCount,
    status = status,
)

/** A plan's status document. */
private fun PlanStatusDto.toPlanStatus(): PlanStatus = PlanStatus(
    id = id,
    status = status,
    halted = halted,
    openBranches = openBranches,
    blockedTasks = blockedTasks,
    currentMilestone = currentMilestone,
)

/** The result of validating a plan's definitions. */
private fun PlanValidationDto.toPlanValidation(): PlanValidation = PlanValidation(
    valid = valid,
    planId = planId,
    errors = errors,
    warnings = warnings,
)

/** Whether a plan's generated views are current. */
private fun RenderCheckDto.toRenderCheck(): RenderCheck = RenderCheck(
    ok = ok,
    planId = planId,
    renderIssues = renderIssues,
)
