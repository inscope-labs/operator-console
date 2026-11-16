package com.inscopelabs.buildengine.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The editable document of a Build Engine plan, as PMA holds it while the operator authors it.
 *
 * Build Engine's plan state is a directory, not a record:
 *
 * ```
 * plans/<plan_id>/plan.json
 * plans/<plan_id>/stages.json
 * plans/<plan_id>/tasks/<task-id>.json
 * plans/<plan_id>/coverage_exempt.json
 * ```
 *
 * `PLAN.md` and `CONCURRENCY.md` are generated from those files and are never authored by hand.
 *
 * This model is the operator's *draft* of that directory. It is not a plan: a draft is not
 * executable, is not a work order, and does not appear as an accepted plan until a repository
 * change carrying it has been created and merged. The shapes here mirror the canonical schemas
 * (`plan.schema.json`, `stages.schema.json`, `task.schema.json`, `coverage_exempt.schema.json`)
 * field for field, so the draft can be materialised into those files without translation.
 */
@Serializable
data class PlanDraft(
    /** Local identity of this draft. It is not a plan id and never reaches the repository. */
    val draftId: String,
    /** `plan.json` → `plan_id`. The directory name under `plans/`. */
    val planId: String,
    /** `plan.json` → `title`. */
    val title: String,
    /**
     * `plan.json` → `plan_number`, five digits.
     *
     * Blank means **not reserved**. The authoritative allocator is the Build Engine's plan
     * tooling (`plan_tool.py new`, which draws from `plans/counter.json` and refuses a number
     * already used by another plan). PMA must not mint a number on its own: a number allocated
     * on the device can collide with repository state, and the canonical writer is the only
     * authority for it. Until the number is reserved, task identifiers are provisional.
     */
    val planNumber: String = "",
    /** `plan.json` → `deliverable_roots`. */
    val deliverableRoots: List<String> = emptyList(),
    /** `plan.json` → `deliverable_patterns` (regular expressions). */
    val deliverablePatterns: List<String> = emptyList(),
    /** `plan.json` → `order_rules`. */
    val orderRules: List<String> = emptyList(),
    /** `plan.json` → `max_open_orders`. */
    val maxOpenOrders: Int? = null,
    /** `plan.json` → `spec`. */
    val spec: PlanSpec? = null,
    /** `plan.json` → `verify.commands` — a list of command argv arrays. */
    val verifyCommands: List<List<String>> = emptyList(),
    /** `stages.json` → `phases`. */
    val phases: List<PlanPhase> = emptyList(),
    /** `stages.json` → `stages`. */
    val stages: List<PlanStage> = emptyList(),
    /** `tasks/*.json`, in authoring order. */
    val tasks: List<PlanTaskDraft> = emptyList(),
    /** `coverage_exempt.json` → `items`. */
    val coverageExemptions: List<PlanCoverageExemption> = emptyList(),
    /** Bumped on every save; the local optimistic-concurrency token for a draft. */
    val revision: Int = 0,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    /** True when the plan number has not been reserved by the authoritative allocator. */
    val isPlanNumberReserved: Boolean get() = planNumber.isNotBlank()

    /** The identifier a task's canonical file would carry, before the number is reserved. */
    val planNumberSegment: String get() = planNumber.ifBlank { PlanDraft.PLAN_NUMBER_PLACEHOLDER }

    companion object {
        /**
         * Stands in for the five-digit plan number until the authoritative allocator reserves one.
         * It is deliberately not a valid plan number, so it can never be mistaken for a reserved one.
         */
        const val PLAN_NUMBER_PLACEHOLDER: String = "<NNNNN>"
    }
}

/** `plan.json` → `spec`. */
@Serializable
data class PlanSpec(
    val root: String = "",
    val glob: String = "",
    val itemRegex: String = "",
)

/** `stages.json` → `phases[]`. */
@Serializable
data class PlanPhase(
    /** Digits only, beginning with a non-zero digit. */
    val id: String,
    val name: String,
    val goal: String = "",
)

/** `stages.json` → `stages[]`. */
@Serializable
data class PlanStage(
    /** `PHASE-STAGE`: the phase id, a hyphen, and the stage's ordinal within that phase. */
    val id: String,
    val name: String,
    val goal: String = "",
    /** A gate stage blocks the phases after it until it passes. */
    val gate: Boolean = false,
) {
    val phaseId: String get() = id.substringBefore('-')

    /** The stage's ordinal within its phase — the hundreds digit of a task number. */
    val ordinalWithinPhase: String get() = id.substringAfter('-', "")
}

/** `tasks/<id>.json`. */
@Serializable
data class PlanTaskDraft(
    /**
     * Stable local key for this task inside the draft.
     *
     * The canonical task identifier is derived from the plan number, the task's stage and the
     * task's position within that stage — never typed by hand — so the local key is what the
     * editor and the draft store use to refer to it.
     */
    val localId: String,
    val title: String = "",
    /** The `PHASE-STAGE` id of the stage this task belongs to. */
    val stageId: String = "",
    /**
     * The task's position within its stage — the low two digits of its task number.
     *
     * It is allocated when the task is created and does not move with the task: the canonical
     * identifier of a task is derived from its stage and this number, so a number that changed
     * when the operator reordered the list would change every identifier that depended on it.
     */
    val ordinal: Int = 0,
    val executor: PlanTaskExecutor = PlanTaskExecutor.WORKER,
    val status: PlanTaskStatus = PlanTaskStatus.PLANNED,
    val summary: String = "",
    /** At least one entry; the canonical schema requires it. */
    val acceptance: List<String> = emptyList(),
    val deliverables: List<String> = emptyList(),
    val companions: List<String> = emptyList(),
    val blockedBy: List<PlanDependency> = emptyList(),
    val specRefs: List<String> = emptyList(),
    val context: List<PlanContextRef> = emptyList(),
    val verify: PlanVerify = PlanVerify(),
    val findings: String = "",
)

/** `task.executor`. */
@Serializable
enum class PlanTaskExecutor {
    @SerialName("worker")
    WORKER,

    @SerialName("operator")
    OPERATOR;

    val wireValue: String get() = name.lowercase()
}

/** `task.status`. */
@Serializable
enum class PlanTaskStatus {
    @SerialName("planned")
    PLANNED,

    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("pr_open")
    PR_OPEN,

    @SerialName("done")
    DONE,

    @SerialName("dropped")
    DROPPED,

    @SerialName("superseded")
    SUPERSEDED;

    val wireValue: String get() = name.lowercase()
}

/** `task.blocked_by[].type`. */
@Serializable
enum class PlanDependencyType {
    @SerialName("task")
    TASK,

    @SerialName("decision")
    DECISION,

    @SerialName("item")
    ITEM;

    val wireValue: String get() = name.lowercase()
}

/** `task.blocked_by[]`. */
@Serializable
data class PlanDependency(
    val type: PlanDependencyType,
    val ref: String,
    val resolved: Boolean = false,
)

/** `task.context[].source`. */
@Serializable
enum class PlanContextSource {
    @SerialName("target")
    TARGET,

    @SerialName("plan")
    PLAN;

    val wireValue: String get() = name.lowercase()
}

/** `task.context[].role`. */
@Serializable
enum class PlanContextRole {
    @SerialName("read")
    READ,

    @SerialName("edit")
    EDIT;

    val wireValue: String get() = name.lowercase()
}

/** `task.context[]`. */
@Serializable
data class PlanContextRef(
    val source: PlanContextSource,
    val path: String,
    val label: String = "",
    val role: PlanContextRole = PlanContextRole.READ,
)

/** `task.verify`. */
@Serializable
enum class PlanVerifyMethod {
    @SerialName("command")
    COMMAND,

    @SerialName("manual")
    MANUAL;

    val wireValue: String get() = name.lowercase()
}

/** `task.verify`. */
@Serializable
data class PlanVerify(
    val method: PlanVerifyMethod = PlanVerifyMethod.COMMAND,
    val argv: List<String> = emptyList(),
)

/** `coverage_exempt.json` → `items[]`. */
@Serializable
data class PlanCoverageExemption(
    val id: String,
    val reason: String,
)

/**
 * A draft as it appears in the draft list.
 *
 * Carries no plan content: the list answers "what have I started", not "what does the plan say".
 */
data class PlanDraftSummary(
    val draftId: String,
    val planId: String,
    val title: String,
    val updatedAtEpochMs: Long,
    val revision: Int,
    val phaseCount: Int,
    val stageCount: Int,
    val taskCount: Int,
    val isPlanNumberReserved: Boolean,
)
