package com.inscopelabs.buildengine.domain.usecase.common

import com.inscopelabs.buildengine.domain.repository.TaskRepository

/**
 * Checks that a branch has not moved since the operator last saw it.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the capability's head field makes the operation race-safe. If the
 * branch moved after minting, the write fails and must be re-minted.
 */
class VerifyBranchHeadUseCase(private val taskRepository: TaskRepository) {

    suspend operator fun invoke(taskId: String, expectedHead: String): Boolean =
        TODO("SCAFFOLD: compare the live head SHA with the expected one.")
}
