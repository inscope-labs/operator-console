package com.inscopelabs.buildengine.domain.validation

import com.inscopelabs.buildengine.domain.authoring.PlanDraftAllocator
import com.inscopelabs.buildengine.domain.model.DraftIssue
import com.inscopelabs.buildengine.domain.model.DraftIssueSeverity
import com.inscopelabs.buildengine.domain.model.DraftValidationReport
import com.inscopelabs.buildengine.domain.model.PlanDependencyType
import com.inscopelabs.buildengine.domain.model.PlanDraft
import com.inscopelabs.buildengine.domain.model.PlanTaskDraft
import com.inscopelabs.buildengine.domain.model.PlanVerifyMethod

/**
 * Validates a plan draft against the canonical Build Engine plan rules.
 *
 * The rules are the canonical ones — they are the same checks `plan_tool.py check` applies to a
 * plan directory, taken from the canonical schemas (`plan.schema.json`, `stages.schema.json`,
 * `task.schema.json`, `coverage_exempt.schema.json`) and from the semantic checks in the plan
 * tooling:
 *
 * - identifiers: `plan_id` is lowercase-and-dashes; a phase is digits; a stage is `PHASE-STAGE`;
 *   a task is `NNNNN-PHASE-TTT-SS`, and the stage is the hundreds digit of the task number.
 * - ordering: stages are listed in ascending id order, and a stage's phase is declared.
 * - paths: a deliverable or companion path is relative, has no `.` or `..` component, and — when
 *   the plan declares roots or patterns — sits inside the roots and matches the patterns.
 * - dependencies: a task dependency names a task that exists, and the dependency graph has no
 *   cycles.
 *
 * Two things this validator deliberately does **not** do:
 *
 * - It does not decide that a plan is acceptable. The canonical tooling is the authority; these
 *   findings are what the operator acts on, and the authoritative check is a separate step that
 *   is currently unavailable (see [DraftValidationReport.canonicalServerValidationAvailable]).
 * - It does not check the plan's uniqueness against repository state — that needs the
 *   authoritative allocator and the repository, neither of which the device can see.
 */
class PlanDraftValidator {

    fun validate(draft: PlanDraft): DraftValidationReport {
        val errors = mutableListOf<DraftIssue>()
        val warnings = mutableListOf<DraftIssue>()

        validatePlan(draft, errors)
        validatePhases(draft, errors)
        validateStages(draft, errors)
        validateTasks(draft, errors, warnings)
        validateCoverageExemptions(draft, errors)

        return DraftValidationReport(
            errors = errors,
            warnings = warnings,
        )
    }

    // -- plan.json --

    private fun validatePlan(draft: PlanDraft, errors: MutableList<DraftIssue>) {
        if (!PLAN_ID_REGEX.matches(draft.planId)) {
            errors += error(
                "plan.plan_id",
                "plan id must be lowercase letters, digits and dashes, beginning with a letter or a digit",
            )
        }
        if (draft.title.isBlank()) {
            errors += error("plan.title", "title is empty")
        }
        when {
            draft.planNumber.isBlank() -> errors += error(
                "plan.plan_number",
                "plan number is not reserved: the authoritative Build Engine allocator must assign it " +
                    "before the plan can be submitted, and task identifiers stay provisional until then",
            )
            !PLAN_NUMBER_REGEX.matches(draft.planNumber) -> errors += error(
                "plan.plan_number",
                "plan number must be exactly 5 digits",
            )
        }
        draft.deliverablePatterns.forEachIndexed { index, pattern ->
            if (!compiles(pattern)) {
                errors += error(
                    "plan.deliverable_patterns[$index]",
                    "deliverable pattern '$pattern' is not a valid regular expression",
                )
            }
        }
        draft.maxOpenOrders?.let { limit ->
            if (limit < 1) {
                errors += error("plan.max_open_orders", "max_open_orders must be at least 1")
            }
        }
        draft.verifyCommands.forEachIndexed { index, argv ->
            if (argv.isEmpty()) {
                errors += error("plan.verify[$index]", "a verification command must have at least one argument")
            }
        }
    }

    // -- stages.json -> phases --

    private fun validatePhases(draft: PlanDraft, errors: MutableList<DraftIssue>) {
        if (draft.phases.isEmpty()) {
            errors += error("phases", "a plan declares at least one phase")
        }
        val seen = mutableSetOf<String>()
        draft.phases.forEachIndexed { index, phase ->
            if (!PHASE_ID_REGEX.matches(phase.id)) {
                errors += error("phases[$index].id", "phase id '${phase.id}' must be digits beginning with a non-zero digit")
            }
            if (phase.name.isBlank()) {
                errors += error("phases[$index].name", "phase name is empty")
            }
            if (!seen.add(phase.id)) {
                errors += error("phases[$index].id", "duplicate phase id '${phase.id}'")
            }
        }
    }

    // -- stages.json -> stages --

    private fun validateStages(draft: PlanDraft, errors: MutableList<DraftIssue>) {
        if (draft.stages.isEmpty()) {
            errors += error("stages", "a plan declares at least one stage")
        }
        val phaseIds = draft.phases.map { it.id }.toSet()
        val previous = mutableListOf<Pair<Int, Int>>()
        draft.stages.forEachIndexed { index, stage ->
            if (!STAGE_ID_REGEX.matches(stage.id)) {
                errors += error(
                    "stages[$index].id",
                    "stage id '${stage.id}' must be PHASE-STAGE, for example '1-1'",
                )
            }
            if (stage.name.isBlank()) {
                errors += error("stages[$index].name", "stage name is empty")
            }
            if (stage.id.isNotEmpty() && stage.phaseId !in phaseIds) {
                errors += error(
                    "stages[$index].id",
                    "stage ${stage.id}: phase ${stage.phaseId} is not declared in phases",
                )
            }
            val key = stageSortKey(stage.id)
            if (key != null) {
                val last = previous.lastOrNull()
                if (last != null && isBefore(key, last)) {
                    errors += error("stages", "stages must be listed in ascending id order")
                }
                previous += key
            }
        }
    }

    // -- tasks/*.json --

    private fun validateTasks(
        draft: PlanDraft,
        errors: MutableList<DraftIssue>,
        warnings: MutableList<DraftIssue>,
    ) {

        draft.tasks.forEachIndexed { index, task ->
            val field = "tasks[$index]"
            if (task.title.isBlank()) {
                errors += error("$field.title", "title is empty")
            }
            if (task.stageId.isBlank()) {
                errors += error("$field.stage", "no stage is assigned")
            } else if (PlanDraftAllocator.stageOf(draft, task) == null) {
                errors += error("$field.stage", "unknown stage '${task.stageId}'")
            }
            if (task.acceptance.isEmpty()) {
                errors += error("$field.acceptance", "a task declares at least one acceptance criterion")
            }
            task.acceptance.forEachIndexed { criterionIndex, criterion ->
                if (criterion.isBlank()) {
                    errors += error("$field.acceptance[$criterionIndex]", "acceptance criterion is empty")
                }
            }
            validateTaskPaths(draft, task, field, errors)
            validateTaskContext(task, field, errors)
            validateTaskVerify(task, field, errors, warnings)
            validateTaskDependencies(draft, task, field, errors, warnings)
            validateDerivedIdentifier(draft, task, field, errors)

            if (task.deliverables.isEmpty()) {
                warnings += DraftIssue(
                    severity = DraftIssueSeverity.WARNING,
                    field = "$field.deliverables",
                    message = "task declares no deliverables",
                )
            }
        }

        validateNoDependencyCycles(draft, errors)
    }

    private fun validateTaskPaths(
        draft: PlanDraft,
        task: PlanTaskDraft,
        field: String,
        errors: MutableList<DraftIssue>,
    ) {
        val patterns = draft.deliverablePatterns.mapNotNull { pattern ->
            if (compiles(pattern)) Regex(pattern) else null
        }
        val roots = draft.deliverableRoots.filter { it.isNotBlank() }

        for ((label, paths) in listOf("deliverables" to task.deliverables, "companions" to task.companions)) {
            paths.forEachIndexed { pathIndex, path ->
                val pathField = "$field.$label[$pathIndex]"
                if (path.isBlank()) {
                    errors += error(pathField, "$label path is empty")
                    return@forEachIndexed
                }
                if (!isSafeRelativePath(path)) {
                    errors += error(pathField, "unsafe $label path '$path'")
                    return@forEachIndexed
                }
                if (roots.isNotEmpty() && roots.none { root -> path.startsWith(root) }) {
                    errors += error(pathField, "$label path '$path' is outside deliverable_roots")
                    return@forEachIndexed
                }
                if (patterns.isNotEmpty() && patterns.none { pattern -> pattern.containsMatchIn(path) }) {
                    errors += error(pathField, "$label path '$path' does not match deliverable_patterns")
                    return@forEachIndexed
                }
            }
        }
    }

    private fun validateTaskContext(
        task: PlanTaskDraft,
        field: String,
        errors: MutableList<DraftIssue>,
    ) {
        task.context.forEachIndexed { index, context ->
            if (context.path.isBlank()) {
                errors += error("$field.context[$index].path", "context path is empty")
            } else if (!isSafeRelativePath(context.path)) {
                errors += error("$field.context[$index].path", "unsafe context path '${context.path}'")
            }
        }
    }

    private fun validateTaskVerify(
        task: PlanTaskDraft,
        field: String,
        errors: MutableList<DraftIssue>,
        warnings: MutableList<DraftIssue>,
    ) {
        if (task.verify.method == PlanVerifyMethod.COMMAND && task.verify.argv.isEmpty()) {
            warnings += DraftIssue(
                severity = DraftIssueSeverity.WARNING,
                field = "$field.verify",
                message = "verification method is 'command' but no command is given",
            )
        }
    }

    private fun validateTaskDependencies(
        draft: PlanDraft,
        task: PlanTaskDraft,
        field: String,
        errors: MutableList<DraftIssue>,
        warnings: MutableList<DraftIssue>,
    ) {
        task.blockedBy.forEachIndexed { index, dependency ->
            val dependencyField = "$field.blocked_by[$index]"
            if (dependency.ref.isBlank()) {
                errors += error(dependencyField, "dependency reference is empty")
                return@forEachIndexed
            }
            if (dependency.type != PlanDependencyType.TASK) return@forEachIndexed

            // A task dependency names one of this plan's tasks. It is held against the task's
            // local key, because the canonical identifier is provisional until the plan number
            // is reserved.
            if (draft.tasks.none { it.localId == dependency.ref }) {
                errors += error(dependencyField, "blocked_by unknown task '${dependency.ref}'")
            }
        }
        // A decision is resolved outside the plan, so the device cannot check it.
        if (task.blockedBy.any { it.type == PlanDependencyType.DECISION }) {
            warnings += DraftIssue(
                severity = DraftIssueSeverity.WARNING,
                field = "$field.blocked_by",
                message = "decision dependencies are resolved outside the plan and cannot be checked on the device",
            )
        }
    }

    private fun validateDerivedIdentifier(
        draft: PlanDraft,
        task: PlanTaskDraft,
        field: String,
        errors: MutableList<DraftIssue>,
    ) {
        val identifier = PlanDraftAllocator.taskId(draft, task)
        if (!TASK_ID_REGEX.matches(identifier)) {
            errors += error(
                "$field.id",
                "task identifier '$identifier' is not the canonical NNNNN-PHASE-TTT-SS shape" +
                    if (!draft.isPlanNumberReserved) {
                        " (the plan number is not reserved yet)"
                    } else {
                        ""
                    },
            )
        }
        val stage = PlanDraftAllocator.stageOf(draft, task)
        if (stage != null) {
            val taskNumber = PlanDraftAllocator.taskNumber(draft, task)
            val stageOrdinal = stage.ordinalWithinPhase.toIntOrNull()
            if (taskNumber != null && stageOrdinal != null && taskNumber / 100 != stageOrdinal) {
                errors += error(
                    "$field.id",
                    "task number $taskNumber does not belong to stage ${stage.id} (hundreds digit)",
                )
            }
        }
    }

    private fun validateNoDependencyCycles(draft: PlanDraft, errors: MutableList<DraftIssue>) {
        val byLocalId = draft.tasks.associateBy { it.localId }
        val edges = mutableMapOf<String, MutableList<String>>()
        for (task in draft.tasks) {
            val targets = task.blockedBy
                .filter { it.type == PlanDependencyType.TASK }
                .mapNotNull { dependency -> byLocalId[dependency.ref]?.localId }
            edges[task.localId] = targets.toMutableList()
        }

        val state = mutableMapOf<String, Int>()
        val stack = mutableListOf<String>()
        val cycles = mutableListOf<String>()

        fun visit(node: String) {
            state[node] = 1
            stack += node
            for (next in edges[node].orEmpty()) {
                when (state[next] ?: 0) {
                    0 -> visit(next)
                    1 -> {
                        val cycle = stack.subList(stack.indexOf(next), stack.size).joinToString(" -> ")
                        cycles += "$cycle -> $next"
                    }
                }
            }
            stack.removeAt(stack.lastIndex)
            state[node] = 2
        }

        for (task in draft.tasks) {
            if ((state[task.localId] ?: 0) == 0) visit(task.localId)
        }

        cycles.distinct().forEach { cycle ->
            errors += error("tasks", "dependency cycle: $cycle")
        }
    }

    // -- coverage_exempt.json --

    private fun validateCoverageExemptions(draft: PlanDraft, errors: MutableList<DraftIssue>) {
        draft.coverageExemptions.forEachIndexed { index, exemption ->
            if (exemption.id.isBlank()) {
                errors += error("coverage[$index].id", "coverage exemption id is empty")
            }
            if (exemption.reason.isBlank()) {
                errors += error("coverage[$index].reason", "coverage exemption reason is empty")
            }
        }
    }

    // -- helpers --

    private fun error(field: String, message: String): DraftIssue =
        DraftIssue(DraftIssueSeverity.ERROR, field, message)

    private fun compiles(pattern: String): Boolean = try {
        Regex(pattern)
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Rejects absolute, Windows, null, empty, `.` and `..` path components.
     *
     * The same rule the canonical plan tooling applies to deliverable and companion paths, so a
     * path that passes here is one the canonical writer will accept.
     */
    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains('\u0000')) {
            return false
        }
        if (WINDOWS_DRIVE_REGEX.containsMatchIn(path)) return false
        return path.split('/').none { part -> part.isEmpty() || part == "." || part == ".." }
    }

    /** Orders stage ids by phase, then by the stage's ordinal within that phase. */
    private fun isBefore(candidate: Pair<Int, Int>, other: Pair<Int, Int>): Boolean =
        candidate.first < other.first ||
            (candidate.first == other.first && candidate.second < other.second)

    private fun stageSortKey(stageId: String): Pair<Int, Int>? {
        val parts = stageId.split('-')
        if (parts.size != 2) return null
        val phase = parts[0].toIntOrNull() ?: return null
        val stage = parts[1].toIntOrNull() ?: return null
        return phase to stage
    }

    companion object {
        private val PLAN_ID_REGEX = Regex("^[a-z0-9][a-z0-9-]*$")
        private val PLAN_NUMBER_REGEX = Regex("^[0-9]{5}$")
        private val PHASE_ID_REGEX = Regex("^[1-9][0-9]*$")
        private val STAGE_ID_REGEX = Regex("^[1-9][0-9]*-[1-9]$")
        private val TASK_ID_REGEX = Regex("^[0-9]{5}-[1-9][0-9]*-[0-9]{3}-[0-9]{2}$")
        private val WINDOWS_DRIVE_REGEX = Regex("^[A-Za-z]:")
    }
}
