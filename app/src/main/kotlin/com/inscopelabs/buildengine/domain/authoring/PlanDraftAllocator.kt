package com.inscopelabs.buildengine.domain.authoring

import com.inscopelabs.buildengine.domain.model.PlanDependency
import com.inscopelabs.buildengine.domain.model.PlanDependencyType
import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.model.PlanTaskDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Derives the canonical Build Engine identifiers and file layout for a plan draft.
 *
 * Two rules drive everything here, and both come from the canonical tooling
 * (`template/engine/tools/plan_tool.py`):
 *
 * 1. **A task identifier is derived, never typed.** Its shape is
 *    `NNNNN-PHASE-TTT-SS` where `NNNNN` is the plan number, `PHASE` is the phase of the task's
 *    stage, `TTT` is the task number and `SS` the sub-task number. The stage is *the hundreds
 *    digit of the task number* — a task in stage `5-2` has a task number between `200` and `299`.
 *    The canonical checker enforces exactly that, so PMA derives the identifier the same way
 *    instead of asking the operator to type it.
 * 2. **A plan number is allocated by the authoritative allocator**, which is the plan tooling's
 *    counter. PMA does not mint one: a number allocated on the device can collide with
 *    repository state. Until the number is reserved, the plan-number segment is a visible
 *    placeholder and the identifiers are provisional.
 */
object PlanDraftAllocator {

    /** The plan-number segment used while the plan number is unreserved. */
    const val PLAN_NUMBER_PLACEHOLDER: String = PlanDraft.PLAN_NUMBER_PLACEHOLDER

    /** The plan-number segment a draft's identifiers currently carry. */
    fun planNumberSegment(draft: PlanDraft): String =
        draft.planNumber.ifBlank { PLAN_NUMBER_PLACEHOLDER }

    /** The stage a task belongs to, or null when the task names no known stage. */
    fun stageOf(draft: PlanDraft, task: PlanTaskDraft) =
        draft.stages.firstOrNull { it.id == task.stageId }

    /**
     * The next free position within a stage.
     *
     * A stage may hold at most one hundred tasks before the hundreds digit — and therefore the
     * stage the identifier points at — would no longer be the task's stage, so the search stops
     * at a hundred.
     */
    fun nextFreeOrdinal(draft: PlanDraft, stageId: String): Int {
        val taken = draft.tasks.filter { it.stageId == stageId }.map { it.ordinal }.toSet()
        for (candidate in 0 until MAX_TASKS_PER_STAGE) {
            if (candidate !in taken) return candidate
        }
        return MAX_TASKS_PER_STAGE
    }

    /**
     * The three-digit task number: the stage's ordinal within its phase, times one hundred, plus
     * the task's own position within that stage.
     */
    fun taskNumber(draft: PlanDraft, task: PlanTaskDraft): Int? {
        val stage = stageOf(draft, task) ?: return null
        val stageOrdinal = stage.ordinalWithinPhase.toIntOrNull() ?: return null
        return stageOrdinal * 100 + task.ordinal
    }

    /**
     * The reference a dependency carries in the canonical file.
     *
     * A task dependency is stored against the task's local key, because the canonical identifier
     * is provisional until the plan number is reserved and would otherwise break the dependency
     * the moment the number arrived. This resolves it to the identifier the file must carry.
     */
    fun resolveDependencyRef(draft: PlanDraft, dependency: PlanDependency): String {
        if (dependency.type != PlanDependencyType.TASK) return dependency.ref
        val target = draft.tasks.firstOrNull { it.localId == dependency.ref } ?: return dependency.ref
        return taskId(draft, target)
    }

    /**
     * The canonical identifier of a task, in the shape `NNNNN-PHASE-TTT-SS`.
     *
     * When the plan number is unreserved the first segment is the placeholder, so the identifier
     * is visibly provisional rather than silently wrong.
     */
    fun taskId(draft: PlanDraft, task: PlanTaskDraft): String {
        val stage = stageOf(draft, task)
        val phase = stage?.phaseId ?: UNKNOWN_SEGMENT
        val number = taskNumber(draft, task) ?: 0
        return "%s-%s-%03d-00".format(planNumberSegment(draft), phase, number)
    }

    /** The repository path a task's canonical file would carry. */
    fun taskFilePath(draft: PlanDraft, task: PlanTaskDraft): String =
        "plans/${draft.planId}/tasks/${taskId(draft, task)}.json"

    /**
     * The files a submitted draft would create, with the content each would carry.
     *
     * This is the preview: the exact canonical documents, so the operator sees what the
     * repository change would contain before anything is submitted. `PLAN.md` and
     * `CONCURRENCY.md` are listed as generated — the tooling writes them, and they are never
     * authored by hand.
     */
    fun materialize(draft: PlanDraft): List<PlannedFile> {
        val files = mutableListOf<PlannedFile>()
        files += PlannedFile(
            path = "plans/${draft.planId}/plan.json",
            kind = PlannedFileKind.PLAN_JSON,
            content = encode(planJson(draft)),
        )
        files += PlannedFile(
            path = "plans/${draft.planId}/stages.json",
            kind = PlannedFileKind.STAGES_JSON,
            content = encode(stagesJson(draft)),
        )
        for (task in draft.tasks) {
            files += PlannedFile(
                path = taskFilePath(draft, task),
                kind = PlannedFileKind.TASK_JSON,
                content = encode(taskJson(draft, task)),
            )
        }
        files += PlannedFile(
            path = "plans/${draft.planId}/coverage_exempt.json",
            kind = PlannedFileKind.COVERAGE_EXEMPT_JSON,
            content = encode(coverageExemptJson(draft)),
        )
        files += PlannedFile(
            path = "plans/${draft.planId}/PLAN.md",
            kind = PlannedFileKind.GENERATED,
            content = GENERATED_NOTE,
        )
        files += PlannedFile(
            path = "plans/${draft.planId}/CONCURRENCY.md",
            kind = PlannedFileKind.GENERATED,
            content = GENERATED_NOTE,
        )
        return files
    }

    // -- canonical document shapes --

    private fun planJson(draft: PlanDraft): JsonObject = buildJsonObject {
        put("plan_id", draft.planId)
        put("title", draft.title)
        put("plan_number", planNumberSegment(draft))
        if (draft.deliverableRoots.isNotEmpty()) {
            put("deliverable_roots", JsonArray(draft.deliverableRoots.map { JsonPrimitive(it) }))
        }
        if (draft.deliverablePatterns.isNotEmpty()) {
            put("deliverable_patterns", JsonArray(draft.deliverablePatterns.map { JsonPrimitive(it) }))
        }
        if (draft.orderRules.isNotEmpty()) {
            put("order_rules", JsonArray(draft.orderRules.map { JsonPrimitive(it) }))
        }
        draft.maxOpenOrders?.let { put("max_open_orders", it) }
        draft.spec?.let { spec ->
            put(
                "spec",
                buildJsonObject {
                    if (spec.root.isNotEmpty()) put("root", spec.root)
                    if (spec.glob.isNotEmpty()) put("glob", spec.glob)
                    if (spec.itemRegex.isNotEmpty()) put("item_regex", spec.itemRegex)
                },
            )
        }
        if (draft.verifyCommands.isNotEmpty()) {
            put(
                "verify",
                buildJsonObject {
                    put(
                        "commands",
                        buildJsonArray {
                            draft.verifyCommands.forEach { argv ->
                                add(buildJsonArray { argv.forEach { arg -> add(JsonPrimitive(arg)) } })
                            }
                        },
                    )
                },
            )
        }
    }

    private fun stagesJson(draft: PlanDraft): JsonObject = buildJsonObject {
        put(
            "phases",
            buildJsonArray {
                draft.phases.forEach { phase ->
                    add(
                        buildJsonObject {
                            put("id", phase.id)
                            put("name", phase.name)
                            if (phase.goal.isNotEmpty()) put("goal", phase.goal)
                        },
                    )
                }
            },
        )
        put(
            "stages",
            buildJsonArray {
                draft.stages.forEach { stage ->
                    add(
                        buildJsonObject {
                            put("id", stage.id)
                            put("name", stage.name)
                            if (stage.goal.isNotEmpty()) put("goal", stage.goal)
                            put("gate", stage.gate)
                        },
                    )
                }
            },
        )
    }

    private fun taskJson(draft: PlanDraft, task: PlanTaskDraft): JsonObject = buildJsonObject {
        put("id", taskId(draft, task))
        put("title", task.title)
        put("stage", task.stageId)
        put("executor", task.executor.wireValue)
        put("status", task.status.wireValue)
        if (task.summary.isNotEmpty()) put("summary", task.summary)
        put("acceptance", JsonArray(task.acceptance.map { JsonPrimitive(it) }))
        put("deliverables", JsonArray(task.deliverables.map { JsonPrimitive(it) }))
        if (task.companions.isNotEmpty()) {
            put("companions", JsonArray(task.companions.map { JsonPrimitive(it) }))
        }
        if (task.blockedBy.isNotEmpty()) {
            put(
                "blocked_by",
                buildJsonArray {
                    task.blockedBy.forEach { dependency ->
                        add(
                            buildJsonObject {
                                put("type", dependency.type.wireValue)
                                put("ref", resolveDependencyRef(draft, dependency))
                                put("resolved", dependency.resolved)
                            },
                        )
                    }
                },
            )
        }
        if (task.specRefs.isNotEmpty()) {
            put("spec_refs", JsonArray(task.specRefs.map { JsonPrimitive(it) }))
        }
        if (task.context.isNotEmpty()) {
            put(
                "context",
                buildJsonArray {
                    task.context.forEach { context ->
                        add(
                            buildJsonObject {
                                put("source", context.source.wireValue)
                                put("path", context.path)
                                if (context.label.isNotEmpty()) put("label", context.label)
                                put("role", context.role.wireValue)
                            },
                        )
                    }
                },
            )
        }
        put(
            "verify",
            buildJsonObject {
                put("method", task.verify.method.wireValue)
                if (task.verify.argv.isNotEmpty()) {
                    put("argv", JsonArray(task.verify.argv.map { JsonPrimitive(it) }))
                }
            },
        )
        if (task.findings.isNotEmpty()) put("findings", task.findings)
    }

    private fun coverageExemptJson(draft: PlanDraft): JsonObject = buildJsonObject {
        put(
            "items",
            buildJsonArray {
                draft.coverageExemptions.forEach { exemption ->
                    add(
                        buildJsonObject {
                            put("id", exemption.id)
                            put("reason", exemption.reason)
                        },
                    )
                }
            },
        )
    }

    private fun encode(jsonObject: JsonObject): String =
        PRETTY.encodeToString(JsonObject.serializer(), jsonObject)

    private const val UNKNOWN_SEGMENT = "0"

    /** A stage holds at most one hundred tasks before the task number overflows its stage. */
    const val MAX_TASKS_PER_STAGE: Int = 100

    private const val GENERATED_NOTE =
        "Generated by the Build Engine plan tooling from the canonical plan files. " +
            "It is never authored by hand."

    private val PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }
}

/** One file a submitted draft would create. */
data class PlannedFile(
    val path: String,
    val kind: PlannedFileKind,
    val content: String,
)

enum class PlannedFileKind {
    PLAN_JSON,
    STAGES_JSON,
    TASK_JSON,
    COVERAGE_EXEMPT_JSON,
    GENERATED,
}
