package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.dto.TaskSummaryDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.repository.TaskRepository

/**
 * The tasks within a plan, as the gateway reports them.
 *
 * The task's status arrives in the gateway's vocabulary — `"ready"` — and is
 * translated here into the console's own. The translation is a fixed table and
 * nothing else: a status the console does not know becomes [TaskStatus.UNKNOWN]
 * rather than a guess, because guessing wrong about whether work has started is
 * worse than admitting ignorance.
 */
class TaskRepositoryImpl(
    private val gateway: GatewayRepository,
    private val errorMapper: ErrorMapper = ErrorMapper(),
) : TaskRepository {

    override suspend fun getNextTasks(): AppResult<List<Task>> = callGateway(errorMapper) {
        gateway.nextTasks().map { it.toTask() }
    }

    /**
     * One task, found in the next-tasks list.
     *
     * The contract has no single-task endpoint, so the list is the only place a
     * task can come from. A task the gateway is not handing out is reported as
     * `not_found`: the console has nothing to describe it with, and a task with
     * blank fields on the operator's screen would be a worse answer than none.
     */
    override suspend fun getTask(taskId: String): AppResult<Task> = callGateway(errorMapper) {
        gateway.nextTasks()
            .firstOrNull { it.id == taskId }
            ?.toTask()
            ?: throw NotFoundException("the gateway hands out no task '$taskId'")
    }

    /**
     * The task's branch and its live head.
     *
     * The branch name comes from the task list and the head from the branch
     * endpoint, and the difference matters: the list's SHA is a snapshot taken when
     * the list was built, while the branch endpoint answers now. A capability is
     * minted against the live head, and one minted against a stale SHA is refused
     * by the gateway as a drift (docs/threat-model.md, V7).
     */
    override suspend fun branchFor(taskId: String): AppResult<TaskBranch> = callGateway(errorMapper) {
        val task = gateway.nextTasks().firstOrNull { it.id == taskId }
            ?: throw NotFoundException("the gateway hands out no task '$taskId'")
        if (task.branch.isEmpty()) {
            throw NotFoundException("the gateway reported no branch for task '$taskId'")
        }
        val head = gateway.branchHead(task.branch)
        TaskBranch(
            name = head.branch.ifEmpty { task.branch },
            headSha = head.headSha,
            // The console evaluates no guards and no read endpoint reports their
            // outcome, so this stays unknown rather than being claimed either way.
            guardsPassed = null,
        )
    }

    /**
     * An operator-executed status change.
     *
     * **Refused, because the gateway contract defines no route for it.** The
     * gateway's plan store port — the compare-and-swap a status change needs — is
     * the gateway's phase 3, and docs/architecture.md §3 lists it as work that is
     * not done. Until the contract names a route and its request body, there is
     * nothing this could call.
     *
     * The refusal is explicit rather than an approximation: a status change sent
     * to the wrong endpoint, or silently dropped, would leave the operator
     * believing a task had moved when it had not.
     */
    override suspend fun setStatus(taskId: String, status: TaskStatus): AppResult<Task> =
        AppResult.Failure(
            code = UNSUPPORTED_OPERATION,
            message = "the gateway contract defines no task-status endpoint; a status change " +
                "is the gateway's plan store port (docs/architecture.md §3), and until the " +
                "contract names a route this operation is refused rather than approximated",
        )

    private companion object {
        /**
         * A console-side failure code, not one of the gateway's states.
         *
         * The gateway's states answer "what did the gateway say?"; this one
         * answers "the console cannot do this at all", which is a different thing
         * and needs a different word on the screen.
         */
        const val UNSUPPORTED_OPERATION: String = "unsupported_operation"
    }
}

/** A task as the gateway reports it. */
private fun TaskSummaryDto.toTask(): Task = Task(
    id = id,
    planId = planId,
    title = title,
    status = status.toTaskStatus(),
    branch = branch,
    headSha = headSha,
)

/**
 * The gateway's task vocabulary, translated.
 *
 * The contract names `"ready"` for a task (docs/gateway-contract.md §3.6) and
 * mentions "unassigned" in the same breath, and it uses `"in_progress"` for a
 * plan's status. The rest of the table follows that vocabulary; everything else is
 * [TaskStatus.UNKNOWN], because a status the console does not recognise is a task
 * it cannot honestly summarise.
 */
private fun String.toTaskStatus(): TaskStatus = when (trim().lowercase()) {
    "ready", "unassigned", "pending", "todo" -> TaskStatus.PENDING
    "in_progress" -> TaskStatus.IN_PROGRESS
    "done", "completed" -> TaskStatus.DONE
    else -> TaskStatus.UNKNOWN
}
