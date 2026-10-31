package com.inscopelabs.buildengine.domain.model

/**
 * A task within a plan.
 *
 * A task is a unit of work, and the operator's decision about it is about a
 * *specific branch at a specific SHA* (design review §5). So the branch and the
 * head SHA travel with the task: the console has to be able to show both before
 * anything is signed, and asking for them separately would mean a second round
 * trip for every row of the task list.
 */
data class Task(
    val id: String,
    val planId: String,
    val title: String,
    val status: TaskStatus,
    /** The minted branch this task works on, e.g. `task-10008-refactor-parser`. */
    val branch: String = "",
    /** The head SHA the gateway reported for that branch. */
    val headSha: String = "",
)

/**
 * The lifecycle of a task.
 *
 * Design review §12: "done" is an assertion of completion and needs evidence.
 *
 * `UNKNOWN` is a state, not a shrug. The gateway's task vocabulary is its own, and
 * a status the console does not recognise is a task it cannot honestly summarise —
 * mapping it onto `PENDING` would be a guess, and a guess about whether work has
 * started is exactly the kind of guess the console must not make (design review
 * §13). The console shows it as unknown and says so.
 */
enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    DONE,

    /** A status the gateway used that this console does not know. */
    UNKNOWN,
}
