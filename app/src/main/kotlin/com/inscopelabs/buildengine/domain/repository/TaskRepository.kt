package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus

/**
 * The tasks within a plan.
 *
 * As with [PlanRepository], every method answers with an [AppResult]: a refusal
 * from the gateway is part of the answer, and the console has to be able to tell
 * a scope refusal from a drifted head from a halt.
 */
interface TaskRepository {

    /** The tasks the gateway is handing out next, in the gateway's own order. */
    suspend fun getNextTasks(): AppResult<List<Task>>

    /**
     * One task.
     *
     * The contract has no single-task endpoint, so this is a lookup in the
     * next-tasks list. A task the gateway is not handing out is a `not_found`
     * failure — the console does not know enough to describe it, and a task with
     * blank fields would be a worse answer than no answer.
     */
    suspend fun getTask(taskId: String): AppResult<Task>

    /**
     * The task's branch and its **live** head.
     *
     * The head comes from the branch endpoint, not from the task list, because the
     * task list's SHA is a snapshot: it is what the branch was at when the list was
     * built. The live head is what a capability would be minted against, and a
     * capability minted against a stale SHA is refused by the gateway
     * (docs/threat-model.md, V7).
     */
    suspend fun branchFor(taskId: String): AppResult<TaskBranch>

    /**
     * An operator-executed status change.
     *
     * Scope: one task.
     */
    suspend fun setStatus(taskId: String, status: TaskStatus): AppResult<Task>
}
