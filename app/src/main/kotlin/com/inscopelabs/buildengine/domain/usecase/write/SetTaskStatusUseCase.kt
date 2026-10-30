package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.OperationResult
import com.inscopelabs.buildengine.domain.model.TaskStatus
import com.inscopelabs.buildengine.domain.repository.TaskRepository
import com.inscopelabs.buildengine.domain.usecase.common.ConfirmAndMintCapabilityUseCase

/**
 * Operator-executed task status change. Scope: one task.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: "done" is an assertion of completion and needs evidence.
 */
class SetTaskStatusUseCase(
    private val taskRepository: TaskRepository,
    private val confirmAndMintCapability: ConfirmAndMintCapabilityUseCase,
) {

    suspend operator fun invoke(taskId: String, status: TaskStatus, note: String?): OperationResult =
        TODO("SCAFFOLD: mint a task-scoped capability, then set the status.")
}
