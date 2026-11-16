package com.inscopelabs.buildengine.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * The plan authoring screen (PMA Stage 1).
 *
 * A distinct authoring route, not an overload of the read-only plan detail screen: one screen
 * holds a draft and edits it, the other inspects a plan the gateway knows about.
 *
 * The banner is deliberate and permanent. A draft is not a plan: it is not executable, it is not
 * a work order, and it does not become an accepted plan until a repository change carrying it has
 * been created and merged. An operator who has just written a plan should never be left thinking
 * the gateway has it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanAuthoringScreen(
    modifier: Modifier = Modifier,
    viewModel: PlanAuthoringViewModel = hiltViewModel(),
    draftId: String? = null,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(draftId) {
        if (!draftId.isNullOrBlank()) viewModel.openDraft(draftId)
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("pma_authoring_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Plan Authoring") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pma_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = state.hasDraft && state.isDirty,
                        modifier = Modifier.testTag("pma_save_button"),
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save draft")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.testTag("pma_loading"))
                }
                state.draft == null -> {
                    EmptyAuthoringState(
                        onCreate = { viewModel.createDraft() },
                        message = state.error,
                    )
                }
                else -> {
                    DraftBanner(state = state)
                    SectionSelector(state = state, onSelect = viewModel::selectSection)
                    when (state.section) {
                        PlanEditorSection.DETAILS -> DetailsSection(state, viewModel)
                        PlanEditorSection.PHASES -> PhasesSection(state, viewModel)
                        PlanEditorSection.TASKS -> TasksSection(state, viewModel)
                        PlanEditorSection.CONSTRAINTS -> ConstraintsSection(state, viewModel)
                        PlanEditorSection.COVERAGE -> CoverageSection(state, viewModel)
                        PlanEditorSection.REVIEW -> ReviewSection(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftBanner(state: PlanEditorState) {
    val draft = state.draft ?: return
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().testTag("pma_draft_banner"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Draft — not submitted",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = "Plan '${draft.planId}' · revision ${draft.revision} · " +
                    "${draft.phases.size} phase(s), ${draft.stages.size} stage(s), ${draft.tasks.size} task(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (!draft.isPlanNumberReserved) {
                Text(
                    text = "The plan number is not reserved, so task identifiers are provisional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (state.isDirty) {
                Text(
                    text = "Unsaved changes.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.testTag("pma_dirty_indicator"),
                )
            }
        }
    }
}

@Composable
private fun SectionSelector(state: PlanEditorState, onSelect: (PlanEditorSection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanEditorSection.entries.take(3).forEach { section ->
                FilterChip(
                    selected = state.section == section,
                    onClick = { onSelect(section) },
                    label = { Text(section.label) },
                    modifier = Modifier.testTag("pma_section_${section.name}"),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlanEditorSection.entries.drop(3).forEach { section ->
                FilterChip(
                    selected = state.section == section,
                    onClick = { onSelect(section) },
                    label = { Text(section.label) },
                    modifier = Modifier.testTag("pma_section_${section.name}"),
                )
            }
        }
    }
}

@Composable
private fun EmptyAuthoringState(onCreate: () -> Unit, message: String?) {
    Card(modifier = Modifier.fillMaxWidth().testTag("pma_empty_state")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "No draft is open.",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "A draft is a plan you are writing on this device. It is not executable and " +
                    "does not become a plan until it has been submitted to the Build Engine and merged.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onCreate, modifier = Modifier.testTag("pma_create_draft_button")) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text("  Create Plan")
            }
        }
    }
}

/** A labelled text field. */
@Composable
internal fun PmaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    testTag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

/** An editable list of one-line strings. */
@Composable
internal fun PmaStringListEditor(
    label: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        values.forEachIndexed { index, value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { updated ->
                        onChange(values.mapIndexed { i, current -> if (i == index) updated else current })
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onChange(values.filterIndexed { i, _ -> i != index }) },
                    modifier = Modifier.testTag("pma_remove_${label}_$index"),
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(values + "") },
            modifier = Modifier.testTag("pma_add_${label}"),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Text("  Add")
        }
    }
}

/** A labelled section block. */
@Composable
internal fun PmaSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth().testTag("pma_section_card_$title")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** A read-only block of generated text, such as a proposed file. */
@Composable
internal fun PmaCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

/** A horizontal rule between blocks. */
@Composable
internal fun PmaDivider() {
    HorizontalDivider()
}
