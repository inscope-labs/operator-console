package com.inscopelabs.buildengine.domain.model

/**
 * A plan of record.
 *
 * A plan is the private document that names the work. It is the thing a capability
 * is bound to, and it is what the operator reads to decide what to touch.
 *
 * `status` is the gateway's own vocabulary, carried as it arrives. The contract
 * defines a plan's status as a string and does not enumerate its values — it only
 * shows `"in_progress"` and says the endpoint lists "active and archived plans" —
 * so the console does not invent an enumeration it would then have to guess at. A
 * screen that wants to distinguish the states compares against the strings the
 * contract uses.
 */
data class Plan(
    val id: String,
    val title: String,
    val taskCount: Int,
    /** How many of the plan's tasks are done. The plan list's progress. */
    val completedTaskCount: Int = 0,
    /** The gateway's status vocabulary, as it arrives. */
    val status: String = "",
)

/**
 * A plan's status document.
 *
 * docs/gateway-contract.md §3.3 — `GET /plans/{id}/status`.
 *
 * This is the document the operator reads *before* approving a write, and it says
 * the two things that would make a mint fail: `halted` is the engine's halt marker,
 * and `openBranches` are the branches the plan currently has open — a branch that
 * has moved is a mint the gateway will refuse.
 *
 * `blockedTasks` is the engine's own list of tasks it will not hand out.
 */
data class PlanStatus(
    val id: String,
    /** The gateway's status vocabulary, as it arrives. */
    val status: String,
    /** Whether the engine is halted. A halt locks writes until somebody resumes. */
    val halted: Boolean,
    /** The branches this plan currently has open. */
    val openBranches: List<String>,
    /** The tasks the engine is not handing out. */
    val blockedTasks: List<String>,
    /** The milestone the plan is on. */
    val currentMilestone: String,
)

/**
 * The result of validating a plan's definitions.
 *
 * docs/gateway-contract.md §3.4 — `POST /plans/{id}/validate`.
 *
 * The errors and the warnings are the whole point of the check. A validation that
 * reports only "invalid" leaves the operator with nothing to act on, so they are
 * carried rather than reduced to a boolean.
 */
data class PlanValidation(
    val valid: Boolean,
    val planId: String,
    val errors: List<String>,
    val warnings: List<String>,
)

/**
 * Whether a plan's generated views are current.
 *
 * docs/gateway-contract.md §3.5 — `GET /plans/{id}/render-check`.
 *
 * `renderIssues` says what is stale, for the same reason the validation's errors
 * do: "not current" without saying which view is stale is a report the operator
 * cannot act on.
 */
data class RenderCheck(
    val ok: Boolean,
    val planId: String,
    val renderIssues: List<String>,
)
