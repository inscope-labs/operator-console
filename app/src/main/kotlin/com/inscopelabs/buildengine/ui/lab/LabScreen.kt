package com.inscopelabs.buildengine.ui.lab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.lab.Scenario
import com.inscopelabs.buildengine.lab.ScenarioResult
import com.inscopelabs.buildengine.ui.common.UiState

/**
 * The banner every Lab surface carries.
 *
 * ADR 0004 §3: "Every UI screen and surface executing within Lab Mode carries a
 * persistent, un-dismissible top banner: **LAB — not an operator approval**."
 *
 * There is no dismiss action on it and no state that can hide it. A Lab scenario is
 * not an operator approval, and the one thing this screen must never do is let an
 * operator mistake a Lab result for one.
 */
@Composable
private fun LabBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("lab_banner"),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "LAB — not an operator approval",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The Lab scenario screen.
 *
 * **Proposed baseline.** This is a pre-execution proposal produced against
 * `docs/PRE-EXECUTION-ONBOARDING.md`, not a completed stage. It has not been compiled,
 * has no test coverage, and is not wired into navigation, DI or any caller. The AI
 * Studio agent executes Stage 5.3, corrects this file against the real use cases,
 * adds the tests, and closes the stage only when CI is green on the pushed SHA (G-04).
 *
 * TODO(Stage 5.3 / T5-301..T5-304): keep the banner, the toggle and the share action
 * exactly as ADR 0004 requires; wire the Lab signer and the environment guard; and add
 * the state tests the gate requires.
 *
 * ### What this screen is for
 *
 * RELEASE-PLAN.md Stage 5.3 / T5-301: the Lab screen lists the scenario catalogue, runs
 * it against a **test gateway only**, and shows what happened. It is a conformance
 * tool. It is not an operator surface, and nothing it shows authorises anything.
 *
 * ### The boundaries it keeps
 *
 * - The banner is always there and cannot be dismissed.
 * - Lab Mode starts **off** and is never persisted. Nothing on this screen writes a
 *   preference, a datastore entry or a saved-state handle for the toggle (T5-304).
 * - The report is shared only when the operator asks for it, and it carries no
 *   capability, no signature and no nonce.
 * - The screen offers no way to act on a Lab result. A Lab scenario that says a
 *   gateway refuses something is information; it is never an approval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(
    state: UiState<LabUiState>,
    modifier: Modifier = Modifier,
    onLabEnabledChange: (Boolean) -> Unit = {},
    onRunAll: () -> Unit = {},
    onRunScenario: (String) -> Unit = {},
    onCancel: () -> Unit = {},
    onClearReport: () -> Unit = {},
    onShareReport: (String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("lab_screen"),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Lab") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
                LabBanner()
            }
        },
    ) { innerPadding ->
        when (state) {
            is UiState.Idle -> Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).testTag("lab_loading"),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Loading the scenario catalogue…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UiState.Loading -> Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).testTag("lab_loading"),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Running scenarios…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UiState.Error -> Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("lab_error"),
                )
            }

            is UiState.Success -> LabContent(
                data = state.data,
                modifier = Modifier.padding(innerPadding),
                onLabEnabledChange = onLabEnabledChange,
                onRunAll = onRunAll,
                onRunScenario = onRunScenario,
                onCancel = onCancel,
                onClearReport = onClearReport,
                onShareReport = onShareReport,
            )
        }
    }
}

@Composable
private fun LabContent(
    data: LabUiState,
    modifier: Modifier = Modifier,
    onLabEnabledChange: (Boolean) -> Unit,
    onRunAll: () -> Unit,
    onRunScenario: (String) -> Unit,
    onCancel: () -> Unit,
    onClearReport: () -> Unit,
    onShareReport: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("lab_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LabControlsCard(
                data = data,
                onLabEnabledChange = onLabEnabledChange,
                onRunAll = onRunAll,
                onCancel = onCancel,
                onClearReport = onClearReport,
                onShareReport = onShareReport,
            )
        }

        if (data.guardMessage.isNotBlank()) {
            item {
                LabGuardCard(message = data.guardMessage)
            }
        }

        data.report?.let { report ->
            if (report.drifts.isNotEmpty()) {
                item {
                    LabDriftCard(drifts = report.drifts.map { it.summary })
                }
            }
        }

        item {
            Text(
                text = "Scenarios (${data.total})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        items(data.scenarios, key = { it.id }) { scenario ->
            LabScenarioRow(
                scenario = scenario,
                result = data.results[scenario.id],
                enabled = data.canRun,
                onRun = { onRunScenario(scenario.id) },
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * The Lab toggle and the run controls.
 */
@Composable
private fun LabControlsCard(
    data: LabUiState,
    modifier: Modifier = Modifier,
    onLabEnabledChange: (Boolean) -> Unit,
    onRunAll: () -> Unit,
    onCancel: () -> Unit,
    onClearReport: () -> Unit,
    onShareReport: (String) -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("lab_controls_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lab Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Off by default, and not remembered between runs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = data.labEnabled,
                    onCheckedChange = onLabEnabledChange,
                    enabled = !data.isRunning,
                    modifier = Modifier.testTag("lab_toggle"),
                )
            }

            Text(
                text = "Scenarios run against the configured gateway and are refused unless " +
                    "it reports the test environment.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRunAll,
                    enabled = data.canRun,
                    modifier = Modifier.testTag("lab_run_all_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Run all")
                }

                if (data.isRunning) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("lab_cancel_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Cancel")
                    }
                }
            }

            if (data.report != null) {
                HorizontalDivider()

                Text(
                    text = "Report",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${data.completed} of ${data.total} scenarios ran against " +
                        "${data.report.environment.ifBlank { "no gateway" }}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val counts = data.report.summary()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountChip(label = "${counts.passed} passed", color = MaterialTheme.colorScheme.primary)
                    if (counts.unexpected > 0) {
                        CountChip(label = "${counts.unexpected} unexpected", color = MaterialTheme.colorScheme.error)
                    }
                    if (counts.transportFailures > 0) {
                        CountChip(label = "${counts.transportFailures} no answer", color = MaterialTheme.colorScheme.error)
                    }
                    if (counts.skipped > 0) {
                        CountChip(label = "${counts.skipped} skipped", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (counts.drifts > 0) {
                        CountChip(label = "${counts.drifts} drifts", color = MaterialTheme.colorScheme.secondary)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onShareReport(data.report.toJson()) },
                        modifier = Modifier.testTag("lab_share_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Share report")
                    }
                    OutlinedButton(
                        onClick = onClearReport,
                        modifier = Modifier.testTag("lab_clear_report_button"),
                    ) {
                        Text("Discard")
                    }
                }

                Text(
                    text = "The report is held in memory only and is shared only when you " +
                        "ask for it. It carries no capability, no signature and no nonce.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The refusal the Lab shows when it will not run.
 */
@Composable
private fun LabGuardCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("lab_guard_card"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The contract drift the last run showed.
 */
@Composable
private fun LabDriftCard(
    drifts: List<String>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("lab_drift_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Contract drift (${drifts.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            drifts.forEach { drift ->
                Text(
                    text = "• $drift",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * One scenario in the catalogue, and its outcome if it has run.
 */
@Composable
private fun LabScenarioRow(
    scenario: Scenario,
    result: ScenarioResult?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onRun: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("lab_scenario_${scenario.id}"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = scenario.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = scenario.id,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result != null) {
                    ResultBadge(result = result)
                }
            }

            if (scenario.errorCode.isNotBlank()) {
                Text(
                    text = "Expects ${scenario.errorCode} (HTTP ${scenario.expectedStatus})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (result != null && result.detail.isNotBlank()) {
                Text(
                    text = result.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("lab_scenario_detail_${scenario.id}"),
                )
            }

            scenario.precondition?.let { precondition ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = "Needs a controlled gateway: $precondition",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            OutlinedButton(
                onClick = onRun,
                enabled = enabled,
                modifier = Modifier.testTag("lab_run_${scenario.id}"),
            ) {
                Text("Run")
            }
        }
    }
}

/**
 * The outcome of a scenario.
 */
@Composable
private fun ResultBadge(
    result: ScenarioResult,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (result) {
        is ScenarioResult.Passed -> "passed" to MaterialTheme.colorScheme.primary
        is ScenarioResult.Unexpected -> "unexpected" to MaterialTheme.colorScheme.error
        is ScenarioResult.TransportFailure -> "no answer" to MaterialTheme.colorScheme.error
        is ScenarioResult.Skipped -> "skipped" to MaterialTheme.colorScheme.tertiary
    }

    Surface(
        modifier = modifier.testTag("lab_result_badge"),
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (result) {
                    is ScenarioResult.Passed -> Icons.Default.CheckCircle
                    is ScenarioResult.Unexpected -> Icons.Default.Warning
                    is ScenarioResult.TransportFailure -> Icons.Default.Warning
                    is ScenarioResult.Skipped -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

/**
 * A count, in a chip.
 */
@Composable
private fun CountChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        enabled = false,
        modifier = modifier,
    )
}
