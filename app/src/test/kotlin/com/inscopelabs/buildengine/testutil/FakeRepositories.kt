package com.inscopelabs.buildengine.testutil

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.DoctorReport
import com.inscopelabs.buildengine.domain.model.Plan
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.model.SyncStatus
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.repository.PlanRepository
import com.inscopelabs.buildengine.domain.repository.TaskRepository

class FakePlanRepository : PlanRepository {
    var getPlansCallCount: Int = 0
    var getPlanCallCount: Int = 0
    var getStatusCallCount: Int = 0
    var syncCallCount: Int = 0
    var doctorCallCount: Int = 0
    var lastSyncBranch: String? = null
    var plansResult: AppResult<List<Plan>> = AppResult.Success(emptyList())
    var singlePlanResult: (String) -> AppResult<Plan> = { id ->
        AppResult.Failure(code = "not_found", message = "Plan not found: $id")
    }
    var statusResult: (String) -> AppResult<PlanStatus> = { id ->
        AppResult.Failure(code = "not_found", message = "Status not found: $id")
    }
    var validateResult: (String) -> AppResult<PlanValidation> = { id ->
        AppResult.Success(PlanValidation(valid = true, planId = id, errors = emptyList(), warnings = emptyList()))
    }
    var renderCheckResult: (String) -> AppResult<RenderCheck> = { id ->
        AppResult.Success(RenderCheck(ok = true, planId = id, renderIssues = emptyList()))
    }
    var syncResult: (String) -> AppResult<SyncStatus> = {
        AppResult.Success(SyncStatus(status = "synced", headSha = "0".repeat(40), inSync = true))
    }
    var doctorResult: () -> AppResult<DoctorReport> = {
        AppResult.Success(DoctorReport(healthy = true, checks = emptyList()))
    }

    override suspend fun getPlans(): AppResult<List<Plan>> {
        getPlansCallCount++
        return plansResult
    }
    override suspend fun getPlan(planId: String): AppResult<Plan> {
        getPlanCallCount++
        return singlePlanResult(planId)
    }
    override suspend fun getStatus(planId: String): AppResult<PlanStatus> {
        getStatusCallCount++
        return statusResult(planId)
    }
    override suspend fun validate(planId: String): AppResult<PlanValidation> = validateResult(planId)
    override suspend fun renderCheck(planId: String): AppResult<RenderCheck> = renderCheckResult(planId)
    override suspend fun sync(branch: String): AppResult<SyncStatus> {
        syncCallCount++
        lastSyncBranch = branch
        return syncResult(branch)
    }
    override suspend fun doctor(): AppResult<DoctorReport> {
        doctorCallCount++
        return doctorResult()
    }
}

class FakeTaskRepository : TaskRepository {
    var getNextTasksCallCount: Int = 0
    var nextTasksResult: AppResult<List<Task>> = AppResult.Success(emptyList())
    var singleTaskResult: (String) -> AppResult<Task> = { id ->
        AppResult.Failure(code = "not_found", message = "Task not found: $id")
    }
    var branchResult: (String) -> AppResult<TaskBranch> = { id ->
        AppResult.Failure(code = "not_found", message = "Branch not found: $id")
    }
    var liveHeadResult: (String) -> AppResult<String> = { branch ->
        AppResult.Failure(code = "not_found", message = "Live head not found: $branch")
    }
    var setStatusResult: (String, TaskStatus) -> AppResult<Task> = { _, _ ->
        AppResult.Failure(code = "not_implemented", message = "Not implemented")
    }

    override suspend fun getNextTasks(): AppResult<List<Task>> {
        getNextTasksCallCount++
        return nextTasksResult
    }
    override suspend fun getTask(taskId: String): AppResult<Task> = singleTaskResult(taskId)
    override suspend fun branchFor(taskId: String): AppResult<TaskBranch> = branchResult(taskId)
    override suspend fun liveHead(branch: String): AppResult<String> = liveHeadResult(branch)
    override suspend fun setStatus(taskId: String, status: TaskStatus): AppResult<Task> = setStatusResult(taskId, status)
}
