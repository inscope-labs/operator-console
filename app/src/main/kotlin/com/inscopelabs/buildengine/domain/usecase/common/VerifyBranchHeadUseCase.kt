package com.inscopelabs.buildengine.domain.usecase.common

import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.domain.repository.TaskRepository

/**
 * Outcome from checking the remote branch head SHA.
 */
sealed interface VerifyBranchHeadResult {
    data object Match : VerifyBranchHeadResult
    data class Drifted(val liveSha: String) : VerifyBranchHeadResult
    data class Unavailable(val reason: String) : VerifyBranchHeadResult
}

/**
 * Checks that a branch has not moved since the operator last saw it.
 *
 * Design review §5: the capability's head field makes the operation race-safe. If the
 * branch moved after minting, the write fails and must be re-minted.
 */
class VerifyBranchHeadUseCase(private val taskRepository: TaskRepository) {

    /**
     * Compares the live head SHA of the task's branch with the expected head.
     * Returns [VerifyBranchHeadResult.Match] if matching, [VerifyBranchHeadResult.Drifted] if
     * drifted, or [VerifyBranchHeadResult.Unavailable] if checking failed.
     */
    suspend operator fun invoke(branchOrTaskId: String, expectedHead: String): VerifyBranchHeadResult {
        if (branchOrTaskId.isBlank() || expectedHead.isBlank()) {
            return VerifyBranchHeadResult.Unavailable("Branch/task ID or expected head is blank")
        }
        val expected = expectedHead.trim()

        // 1. Try reading liveHead directly for the branch name
        val liveHeadResult = taskRepository.liveHead(branchOrTaskId)
        if (liveHeadResult is AppResult.Success) {
            val liveSha = liveHeadResult.value
            return if (liveSha.equals(expected, ignoreCase = true)) {
                VerifyBranchHeadResult.Match
            } else {
                VerifyBranchHeadResult.Drifted(liveSha)
            }
        }

        // 2. If branchOrTaskId is a taskId, resolve via branchFor
        val branchResult = taskRepository.branchFor(branchOrTaskId)
        return when (branchResult) {
            is AppResult.Success -> {
                val liveSha = branchResult.value.headSha
                if (liveSha.equals(expected, ignoreCase = true)) {
                    VerifyBranchHeadResult.Match
                } else {
                    VerifyBranchHeadResult.Drifted(liveSha)
                }
            }
            is AppResult.Failure -> {
                VerifyBranchHeadResult.Unavailable(branchResult.message)
            }
        }
    }
}
