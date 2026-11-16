package com.inscopelabs.buildengine.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.domain.authoring.PlanDraftAllocator
import com.inscopelabs.buildengine.domain.model.DraftIssue
import com.inscopelabs.buildengine.domain.model.DraftIssueSeverity
import com.inscopelabs.buildengine.domain.model.PlanDependency
import com.inscopelabs.buildengine.domain.model.PlanDependencyType
import com.inscopelabs.buildengine.domain.model.PlanTaskDraft
import com.inscopelabs.buildengine.domain.model.PlanTaskExecutor
import com.inscopelabs.buildengine.domain.model.PlanTaskStatus
import com.inscopelabs.buildengine.domain.model.PlanVerifyMethod

/**
 * The authoring editor's sections.
 *
 * Each section is the editor for one part of the canonical plan document, and each one edits the
 * draft and nothing else: the draft is the single source of truth, and everything else on the
 * screen — the validation report, the preview — is derived from it.
 */
@Composable
internal fun DetailsSection(state: PlanEditorState, viewModel: PlanAuthoringViewModel) {
    val draft = state.draft ?: return
    PmaSection(title = "Plan details") {
        PmaTextField(
            label = "Plan id (the plans/<plan_id>/ directory name)",
            value = draft.planId,
            onValueChange = viewModel::updatePlanId,
            testTag = "pma_plan_id_field",
        )
        PmaTextField(
            label = "Title",
            value = draft.title,
            onValueChange = viewModel::updateTitle,
            testTag = "pma_title_field",
        )
        Text(
            text = if (draft.isPlanNumberReserved) {
                "Plan number ${draft.planNumber} (reserved)."
            } else {
                "Plan number: not reserved. The Build Engine's plan tooling owns the number, so PMA " +
                    "does not allocate one; task identifiers stay provisional until it is reserved."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("pma_plan_number_state"),
        )
    }
}

@Composable
internal fun PhasesSection(state: PlanEditorState, viewModel: PlanAuthoringViewModel) {
    val draft = state.draft ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PmaSection(title = "Phases") {
            if (draft.phases.isEmpty()) {
                Text(
                    text = "A plan declares at least one phase.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            draft.phases.forEachIndexed { index, phase ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Phase ${phase.id}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.removePhase(index) },
                            modifier = Modifier.testTag("pma_remove_phase_${phase.id}"),
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove phase")
                        }
                    }
                    PmaTextField(
                        label = "Name",
                        value = phase.name,
                        onValueChange = { name ->
                            viewModel.updatePhase(index, name, phase.goal)
                        },
                    )
                    PmaTextField(
                        label = "Goal",
                        value = phase.goal,
                        onValueChange = { goal ->
                            viewModel.updatePhase(index, phase.name, goal)
                        },
                        singleLine = false,
                    )
                }
                PmaDivider()
            }
            OutlinedButton(
                onClick = { viewModel.addPhase() },
                modifier = Modifier.testTag("pma_add_phase_button"),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text("  Add phase")
            }
        }

        PmaSection(title = "Stages") {
            if (draft.stages.isEmpty()) {
                Text(
                    text = "A plan declares at least one stage. A stage id is PHASE-STAGE, and a task's " +
                        "stage is the hundreds digit of its task number.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            draft.stages.forEachIndexed { index, stage ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Stage ${stage.id}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.removeStage(index) },
                            modifier = Modifier.testTag("pma_remove_stage_${stage.id}"),
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove stage")
                        }
                    }
                    PmaTextField(
                        label = "Name",
                        value = stage.name,
                        onValueChange = { name ->
                            viewModel.updateStage(index, name, stage.goal, stage.gate)
                        },
                    )
                    PmaTextField(
                        label = "Goal",
                        value = stage.goal,
                        onValueChange = { goal ->
                            viewModel.updateStage(index, stage.name, goal, stage.gate)
                        },
                        singleLine = false,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = stage.gate,
                            onClick = {
                                viewModel.updateStage(index, stage.name, stage.goal, !stage.gate)
                            },
                            label = { Text("Gate stage") },
                            modifier = Modifier.testTag("pma_stage_gate_${stage.id}"),
                        )
                    }
                }
                PmaDivider()
            }
            draft.phases.forEach { phase ->
                OutlinedButton(
                    onClick = { viewModel.addStage(phase.id) },
                    modifier = Modifier.testTag("pma_add_stage_${phase.id}"),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text("  Add stage to phase ${phase.id}")
                }
            }
        }
    }
}

@Composable
internal fun TasksSection(state: PlanEditorState, viewModel: PlanAuthoringViewModel) {
    val draft = state.draft ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PmaSection(title = "Tasks") {
            if (draft.tasks.isEmpty()) {
                Text(
                    text = "No tasks yet. A task's identifier is derived from the plan number, its stage " +
                        "and its position within that stage — it is never typed by hand.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            draft.tasks.forEachIndexed { index, task ->
                TaskEditor(
                    draft = draft,
                    task = task,
                    index = index,
                    viewModel = viewModel,
                )
                PmaDivider()
            }
            OutlinedButton(
                onClick = { viewModel.addTask() },
                enabled = draft.stages.isNotEmpty(),
                modifier = Modifier.testTag("pma_add_task_button"),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text("  Add task")
            }
            if (draft.stages.isEmpty()) {
                Text(
                    text = "A task needs a stage, so declare a stage first.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TaskEditor(
    draft: com.inscopelabs.buildengine.domain.model.PlanDraft,
    task: PlanTaskDraft,
    index: Int,
    viewModel: PlanAuthoringViewModel,
) {
    val derivedId = PlanDraftAllocator.taskId(draft, task)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = derivedId,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f).testTag("pma_task_id_$index"),
            )
            IconButton(
                onClick = { viewModel.removeTask(task.localId) },
                modifier = Modifier.testTag("pma_remove_task_$index"),
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove task")
            }
        }
        PmaTextField(
            label = "Title",
            value = task.title,
            onValueChange = { title -> viewModel.updateTask(task.localId) { it.copy(title = title) } },
            testTag = "pma_task_title_$index",
        )
        PmaTextField(
            label = "Summary",
            value = task.summary,
            onValueChange = { summary -> viewModel.updateTask(task.localId) { it.copy(summary = summary) } },
            singleLine = false,
        )

        Text(text = "Stage", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            draft.stages.take(MAX_VISIBLE_CHOICES).forEach { stage ->
                FilterChip(
                    selected = task.stageId == stage.id,
                    onClick = { viewModel.setTaskStage(task.localId, stage.id) },
                    label = { Text(stage.id) },
                    modifier = Modifier.testTag("pma_task_stage_${index}_${stage.id}"),
                )
            }
        }

        Text(text = "Executor", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanTaskExecutor.entries.forEach { executor ->
                FilterChip(
                    selected = task.executor == executor,
                    onClick = { viewModel.setTaskExecutor(task.localId, executor) },
                    label = { Text(executor.wireValue) },
                    modifier = Modifier.testTag("pma_task_executor_${index}_${executor.name}"),
                )
            }
        }

        Text(text = "Status", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanTaskStatus.entries.forEach { status ->
                FilterChip(
                    selected = task.status == status,
                    onClick = { viewModel.setTaskStatus(task.localId, status) },
                    label = { Text(status.wireValue) },
                    modifier = Modifier.testTag("pma_task_status_${index}_${status.name}"),
                )
            }
        }

        PmaStringListEditor(
            label = "Acceptance criteria",
            values = task.acceptance,
            onChange = { values -> viewModel.updateTask(task.localId) { it.copy(acceptance = values) } },
            modifier = Modifier.testTag("pma_task_acceptance_$index"),
        )
        PmaStringListEditor(
            label = "Deliverables",
            values = task.deliverables,
            onChange = { values -> viewModel.updateTask(task.localId) { it.copy(deliverables = values) } },
            modifier = Modifier.testTag("pma_task_deliverables_$index"),
        )
        PmaStringListEditor(
            label = "Companions",
            values = task.companions,
            onChange = { values -> viewModel.updateTask(task.localId) { it.copy(companions = values) } },
            modifier = Modifier.testTag("pma_task_companions_$index"),
        )

        Text(text = "Blocked by", style = MaterialTheme.typography.labelLarge)
        task.blockedBy.forEachIndexed { dependencyIndex, dependency ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${dependency.type.wireValue}: ${dependency.ref}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { viewModel.removeDependency(task.localId, dependencyIndex) },
                    modifier = Modifier.testTag("pma_remove_dependency_${index}_$dependencyIndex"),
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove dependency")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            draft.tasks.filter { it.localId != task.localId }.take(MAX_VISIBLE_CHOICES).forEach { other ->
                OutlinedButton(
                    onClick = {
                        viewModel.addDependency(
                            task.localId,
                            PlanDependency(type = PlanDependencyType.TASK, ref = other.localId),
                        )
                    },
                    modifier = Modifier.testTag("pma_depend_on_${index}_${other.localId}"),
                ) {
                    Text(PlanDraftAllocator.taskId(draft, other))
                }
            }
        }

        Text(text = "Verification", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanVerifyMethod.entries.forEach { method ->
                FilterChip(
                    selected = task.verify.method == method,
                    onClick = { viewModel.setTaskVerify(task.localId, method, task.verify.argv) },
                    label = { Text(method.wireValue) },
                    modifier = Modifier.testTag("pma_task_verify_${index}_${method.name}"),
                )
            }
        }
        PmaTextField(
            label = "Verification command (space separated)",
            value = task.verify.argv.joinToString(" "),
            onValueChange = { raw ->
                viewModel.setTaskVerify(
                    task.localId,
                    task.verify.method,
                    raw.split(' ').filter { part -> part.isNotBlank() },
                )
            },
            testTag = "pma_task_verify_argv_$index",
        )
    }
}

@Composable
internal fun ConstraintsSection(state: PlanEditorState, viewModel: PlanAuthoringViewModel) {
    val draft = state.draft ?: return
    PmaSection(title = "Deliverable rules and order rules") {
        PmaStringListEditor(
            label = "Deliverable roots",
            values = draft.deliverableRoots,
            onChange = viewModel::updateDeliverableRoots,
            modifier = Modifier.testTag("pma_deliverable_roots"),
        )
        PmaStringListEditor(
            label = "Deliverable patterns",
            values = draft.deliverablePatterns,
            onChange = viewModel::updateDeliverablePatterns,
            modifier = Modifier.testTag("pma_deliverable_patterns"),
        )
        PmaStringListEditor(
            label = "Order rules",
            values = draft.orderRules,
            onChange = viewModel::updateOrderRules,
            modifier = Modifier.testTag("pma_order_rules"),
        )
        PmaTextField(
            label = "Maximum open orders",
            value = draft.maxOpenOrders?.toString().orEmpty(),
            onValueChange = { raw ->
                viewModel.updateMaxOpenOrders(raw.trim().toIntOrNull())
            },
            testTag = "pma_max_open_orders",
        )
    }
}

@Composable
internal fun CoverageSection(state: PlanEditorState, viewModel: PlanAuthoringViewModel) {
    val draft = state.draft ?: return
    PmaSection(title = "Coverage exemptions") {
        Text(
            text = "Specification items this plan explicitly does not cover. Each one needs a reason.",
            style = MaterialTheme.typography.bodySmall,
        )
        draft.coverageExemptions.forEachIndexed { index, exemption ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Exemption ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.removeCoverageExemption(index) },
                        modifier = Modifier.testTag("pma_remove_coverage_$index"),
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove exemption")
                    }
                }
                PmaTextField(
                    label = "Specification item id",
                    value = exemption.id,
                    onValueChange = { id ->
                        viewModel.updateCoverageExemption(index, id, exemption.reason)
                    },
                )
                PmaTextField(
                    label = "Reason",
                    value = exemption.reason,
                    onValueChange = { reason ->
                        viewModel.updateCoverageExemption(index, exemption.id, reason)
                    },
                    singleLine = false,
                )
            }
            PmaDivider()
        }
        OutlinedButton(
            onClick = { viewModel.addCoverageExemption() },
            modifier = Modifier.testTag("pma_add_coverage_button"),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Text("  Add exemption")
        }
    }
}

@Composable
internal fun ReviewSection(state: PlanEditorState, viewModel: PlanAuthoringViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PmaSection(title = "Validate, preview and submit") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.validate() },
                    modifier = Modifier.testTag("pma_validate_button"),
                ) {
                    Text("Validate")
                }
                OutlinedButton(
                    onClick = { viewModel.preview() },
                    modifier = Modifier.testTag("pma_preview_button"),
                ) {
                    Text("Preview")
                }
                OutlinedButton(
                    onClick = { viewModel.requestSubmission() },
                    modifier = Modifier.testTag("pma_submit_button"),
                ) {
                    Text("Submit")
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("pma_message"),
                )
            }
        }

        state.validation?.let { report ->
            PmaSection(title = "Validation") {
                Text(
                    text = if (report.isValid) {
                        "No errors against the canonical rules."
                    } else {
                        "${report.errors.size} error(s) and ${report.warnings.size} warning(s)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("pma_validation_summary"),
                )
                report.errors.forEach { issue ->
                    IssueRow(issue)
                }
                report.warnings.forEach { issue ->
                    IssueRow(issue)
                }
                Text(
                    text = report.canonicalServerValidationNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pma_canonical_validation_note"),
                )
            }
        }

        state.preview?.let { preview ->
            PmaSection(title = "Preview") {
                Text(
                    text = "${preview.authoredFiles.size} authored file(s), " +
                        "${preview.files.size - preview.authoredFiles.size} generated file(s).",
                    style = MaterialTheme.typography.bodySmall,
                )
                preview.files.forEach { file ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("pma_preview_file_${file.path}"),
                        )
                        PmaCodeBlock(text = file.content)
                    }
                    PmaDivider()
                }
            }
        }

        state.submission?.let { outcome ->
            PmaSection(title = "Submission") {
                Text(
                    text = outcome.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("pma_submission_message"),
                )
                outcome.blockers.forEach { blocker ->
                    Text(
                        text = "• $blocker",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("pma_submission_blocker"),
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: DraftIssue) {
    Column(modifier = Modifier.fillMaxWidth().testTag("pma_issue_${issue.field}")) {
        Text(
            text = if (issue.severity == DraftIssueSeverity.ERROR) "Error" else "Warning",
            style = MaterialTheme.typography.labelSmall,
            color = if (issue.severity == DraftIssueSeverity.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(text = issue.message, style = MaterialTheme.typography.bodySmall)
        Text(
            text = issue.field,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How many choices a chip row shows before the editor stops being readable. */
private const val MAX_VISIBLE_CHOICES: Int = 8
