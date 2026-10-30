package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
interface TaskRepository {

    suspend fun getNextTasks(): List<Task>

    suspend fun getTask(taskId: String): Task

    suspend fun branchFor(taskId: String): TaskBranch

    suspend fun setStatus(taskId: String, status: TaskStatus): Task
}
