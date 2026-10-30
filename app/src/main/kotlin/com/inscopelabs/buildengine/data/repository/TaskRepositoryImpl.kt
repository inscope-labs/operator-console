package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.data.remote.GatewayRepository
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.domain.model.TaskBranch
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.repository.TaskRepository

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
class TaskRepositoryImpl(
    private val gateway: GatewayRepository,
) : TaskRepository {

    override suspend fun getNextTasks(): List<Task> = TODO("SCAFFOLD: map the gateway DTOs.")

    override suspend fun getTask(taskId: String): Task = TODO("SCAFFOLD: map one task.")

    override suspend fun branchFor(taskId: String): TaskBranch =
        TODO("SCAFFOLD: resolve the minted branch and its head SHA.")

    override suspend fun setStatus(taskId: String, status: TaskStatus): Task =
        TODO("SCAFFOLD: operator-executed task status change.")
}
