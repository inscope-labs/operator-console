package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.repository.TaskRepository

/**
 * Returns the next actionable tasks.
 *
 * The order is the gateway's. The gateway knows the plan's order and the engine's
 * state, and the console has neither, so it does not re-sort what it is given:
 * a list the operator reads in a different order from the plan's is a list they
 * cannot reconcile with the plan.
 */
class GetNextTasksUseCase(private val taskRepository: TaskRepository) {

    suspend operator fun invoke(): AppResult<List<Task>> = taskRepository.getNextTasks()
}
