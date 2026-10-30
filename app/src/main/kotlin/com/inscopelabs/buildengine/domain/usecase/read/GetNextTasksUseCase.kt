package com.inscopelabs.buildengine.domain.usecase.read

import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.repository.TaskRepository

/**
 * Returns the next actionable tasks.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class GetNextTasksUseCase(private val taskRepository: TaskRepository) {
    suspend operator fun invoke(): List<Task> = TODO("SCAFFOLD: return the next tasks.")
}
