package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.DoctorReport
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.model.SyncStatus

/**
 * The plans of record.
 *
 * Every method answers with an [AppResult], because the gateway's refusals are
 * part of the answer and the UI needs to tell them apart. The four states of
 * design review §12 — Scope Refused, Replayed, Head Drifted, Halt Active — are
 * different situations with different remedies, and a repository that answered
 * with a bare value would collapse them into one failure the operator cannot act
 * on.
 *
 * The failure's `code` is the state the console branches on; its `message` carries
 * the gateway's own reason, so nothing the gateway said is lost.
 */
interface PlanRepository {

    /** Every plan the gateway lists, active and archived alike. */
    suspend fun getPlans(): AppResult<List<Plan>>

    /**
     * One plan.
     *
     * The contract has no single-plan endpoint — §3.2 lists the plans and §3.3
     * gives a plan's *status* — so this is a lookup in the list. A plan that is not
     * in the list is a `not_found` failure, not an empty plan.
     */
    suspend fun getPlan(planId: String): AppResult<Plan>

    /**
     * A plan's status document: whether the engine is halted, which branches are
     * open, which tasks are blocked.
     */
    suspend fun getStatus(planId: String): AppResult<PlanStatus>

    /** Read-only validation of a plan's definitions. */
    suspend fun validate(planId: String): AppResult<PlanValidation>

    /** Whether a plan's generated views are current. */
    suspend fun renderCheck(planId: String): AppResult<RenderCheck>

    /** Verifies consumer repository sync against upstream tracking (`POST /sync`, §3.13). */
    suspend fun sync(branch: String = "main"): AppResult<SyncStatus>

    /** Runs the gateway diagnostic health check (`GET /doctor`, §3.14). */
    suspend fun doctor(): AppResult<DoctorReport>
}
