package com.inscopelabs.buildengine.domain.model

/**
 * A task within a plan.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 */
data class Task(
    val id: String,
    val planId: String,
    val title: String,
    val status: TaskStatus,
)

/**
 * The lifecycle of a task.
 *
 * Design review §12: "done" is an assertion of completion and needs evidence.
 */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
}
