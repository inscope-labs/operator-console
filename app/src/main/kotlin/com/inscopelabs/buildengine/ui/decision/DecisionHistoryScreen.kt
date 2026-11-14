package com.inscopelabs.buildengine.ui.decision

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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.ui.audit.AuditRecord
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.ErrorBanner

/**
 * The operations whose audit entries are decisions.
 *
 * The gateway has one audit journal — `GET /audit` (docs/gateway-contract.md §3.20) —
 * and no separate decisions endpoint. A decision is therefore an audit entry whose
 * `operation` is one of these.
 *
 * The names match `com.inscopelabs.buildengine.capability.model.OperationType`, which is
 * the console's own operation vocabulary, and are matched case-insensitively because the
 * gateway's response schemas allow both `PUBLISH` and `publish` spellings. An entry
 * whose operation is not in this set is not hidden: it is shown under "All operations",
 * because an entry the console cannot classify is still an entry the operator may need.
 */
val DECISION_OPERATIONS: Set<String> = setOf(
    "DECIDE",
    "SET_IN_PROGRESS",
    "SET_DONE",
)

/**
 * True when this audit entry records a decision rather than another kind of operation.
 */
fun AuditRecord.isDecisionEntry(): Boolean =
    operation.trim().uppercase() in DECISION_OPERATIONS

/**
 * The decision history.
 *
 * `records` is the whole audit journal as the gateway returned it. The screen classifies
 * it; the gateway does not hand out a pre-filtered list.
 */
data class DecisionHistoryData(
    val records: List<AuditRecord> = emptyList(),
)

/**
 * The operator's decision history.
 *
 * **Proposed baseline.** This is a pre-execution proposal produced against
 * `docs/PRE-EXECUTION-ONBOARDING.md`, not a completed stage. It has not been compiled,
 * has no test coverage, and is not wired into navigation, DI or any caller. The AI
 * Studio agent executes Stage 4.4, corrects this file against the real gateway read
 * paths, adds the tests, and closes the stage only when CI is green on the pushed SHA
 * (G-04).
 *
 * TODO(Stage 4.4 / T4-401): supply the ViewModel and the audit read path, confirm the
 * decision-operation set against what the gateway actually writes into the audit
 * journal, and add the state tests the gate requires.
 *
 * ## Where the history comes from
 *
 * RELEASE-PLAN.md Stage 4.4 / T4-401: "list past decisions from the gateway read paths".
 * The gateway exposes exactly one such path — `GET /audit` (contract §3.20). There is no
 * `GET /decisions` endpoint and no decision-listing schema, so this screen reads the
 * audit journal and classifies its entries by `operation`. It does not invent a second
 * endpoint (G-07).
 *
 * ## What the history cannot show
 *
 * The contract's audit record carries `auditRef`, `timestamp`, `operation`, `plan`,
 * `task`, `branch`, `headSha`, `kid` and `authTier`. It carries **no rationale, notes or
 * attribution text** — the `notes` field exists on the `decide` *request*
 * (docs/schemas/decide.schema.json) and is not echoed into the audit read path.
 *
 * This screen therefore shows the fields the contract actually exposes and says so
 * plainly rather than presenting an empty rationale slot as if the decision had none.
 * Whether the gateway should expose the decision rationale is an open contract question
 * for the operator (docs/release/PHASE-3-REMEDIATION-REGISTER.md PR3-003); until it is
 * answered, the audit reference is the durable handle for a decision.
 *
 * ## Why the screen holds state instead of taking a ViewModel
 *
 * The screen takes a [UiState] and plain callbacks rather than a ViewModel, so this file
 * stays self-contained against what is on `main` today and leaves one seam for the
 * stage: a `@HiltViewModel` exposing `StateFlow<UiState<DecisionHistoryData>>` and the
 * mapping from `GET /audit` to [AuditRecord].
 *
 * **The signature below is a proposal.** The stage owns the final shape.
 *
 * ## Boundaries this screen keeps
 *
 * - It is read-only. It decides nothing, mints nothing and revokes nothing.
 * - It shows the confirmation tier each decision was made with, and the device key that
 *   made it. A decision whose tier the gateway did not record is shown as unknown, not
 *   as a low tier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionHistoryScreen(
    state: UiState<DecisionHistoryData>,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onOpenAudit: (auditRef: String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("decision_history_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Decision history") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (state) {
            is UiState.Idle -> DecisionIdleContent(
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
            )

            is UiState.Loading -> Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).testTag("decision_history_loading"),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Loading the audit journal…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UiState.Error -> ErrorBanner(
                message = state.message,
                onRetry = onRefresh,
                modifier = Modifier.padding(innerPadding).testTag("decision_history_error"),
            )

            is UiState.Success -> DecisionHistoryContent(
                data = state.data,
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
                onOpenAudit = onOpenAudit,
            )
        }
    }
}

@Composable
private fun DecisionIdleContent(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Nothing loaded yet.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("decision_history_idle"),
        )
        Text(
            text = "The history is read from the gateway's audit journal on demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("decision_history_load_button"),
        ) {
            Text("Load history")
        }
    }
}

@Composable
private fun DecisionHistoryContent(
    data: DecisionHistoryData,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onOpenAudit: (auditRef: String) -> Unit,
) {
    var showAllOperations by remember { mutableStateOf(false) }

    val decisionRecords = remember(data.records) { data.records.filter { it.isDecisionEntry() } }
    val visibleRecords = if (showAllOperations) data.records else decisionRecords

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("decision_history_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DecisionHistoryHeader(
                decisionCount = decisionRecords.size,
                totalCount = data.records.size,
                showAllOperations = showAllOperations,
                onToggleAllOperations = { showAllOperations = !showAllOperations },
                onRefresh = onRefresh,
            )
        }

        if (visibleRecords.isEmpty()) {
            item {
                EmptyDecisionCard(showAllOperations = showAllOperations)
            }
        } else {
            items(visibleRecords, key = { it.auditRef.ifBlank { it.timestampIso } }) { record ->
                DecisionRecordCard(
                    record = record,
                    onOpenAudit = onOpenAudit,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DecisionHistoryHeader(
    decisionCount: Int,
    totalCount: Int,
    showAllOperations: Boolean,
    modifier: Modifier = Modifier,
    onToggleAllOperations: () -> Unit,
    onRefresh: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("decision_history_header"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "$decisionCount decisions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$totalCount audit entries in the journal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("decision_history_refresh_button"),
                ) {
                    Text("Refresh")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showAllOperations,
                    onClick = { if (showAllOperations) onToggleAllOperations() },
                    label = { Text("Decisions") },
                    modifier = Modifier.testTag("decision_history_filter_decisions"),
                )
                FilterChip(
                    selected = showAllOperations,
                    onClick = { if (!showAllOperations) onToggleAllOperations() },
                    label = { Text("All operations") },
                    modifier = Modifier.testTag("decision_history_filter_all"),
                )
            }

            Text(
                text = "The gateway exposes one audit journal, not a separate decisions list. " +
                    "Entries are classified by their operation.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDecisionCard(
    showAllOperations: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("decision_history_empty"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (showAllOperations) {
                    "The audit journal is empty."
                } else {
                    "No decisions in the audit journal."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "A decision appears here once the gateway has recorded it. An empty " +
                    "journal means nothing has been recorded, not that nothing happened.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One decision.
 *
 * The tier is shown as it was recorded. A tier the gateway did not report is shown as
 * "unknown" rather than being folded into the weaker tier — an absent record is not a
 * record of a downgrade.
 */
@Composable
private fun DecisionRecordCard(
    record: AuditRecord,
    modifier: Modifier = Modifier,
    onOpenAudit: (auditRef: String) -> Unit,
) {
    val tier = remember(record.authTier) { AuthTier.fromWireValue(record.authTier) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("decision_record_${record.auditRef}"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.operation.trim().ifBlank { "unknown operation" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TierBadge(tier = tier, recordedTier = record.authTier)
            }

            if (record.plan.isNotBlank() || record.task.isNotBlank()) {
                Text(
                    text = "Plan: ${record.plan.ifBlank { "—" }} · Task: ${record.task.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (record.branch.isNotBlank()) {
                Text(
                    text = "Branch: ${record.branch}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (record.headSha.isNotBlank()) {
                Text(
                    text = "Head: ${record.headSha}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Text(
                text = "At: ${record.timestampIso.ifBlank { "unknown time" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Key: ${record.kid.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Reference: ${record.auditRef.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("decision_record_ref_${record.auditRef}"),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Text(
                    text = "The audit journal does not carry the decision's rationale. " +
                        "The reference above is the durable handle for this decision.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }

            if (record.auditRef.isNotBlank()) {
                OutlinedButton(
                    onClick = { onOpenAudit(record.auditRef) },
                    modifier = Modifier.testTag("decision_open_audit_${record.auditRef}"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Inspect audit record")
                }
            }
        }
    }
}

/**
 * The confirmation tier a decision was made with.
 */
@Composable
private fun TierBadge(
    tier: AuthTier?,
    recordedTier: String,
    modifier: Modifier = Modifier,
) {
    val label = when {
        tier == AuthTier.HARDWARE_CONFIRM -> "hardware_confirm"
        tier == AuthTier.IN_APP_CONFIRM -> "in_app_confirm"
        recordedTier.isBlank() -> "tier not recorded"
        else -> recordedTier
    }

    val container = when (tier) {
        AuthTier.HARDWARE_CONFIRM -> MaterialTheme.colorScheme.primaryContainer
        AuthTier.IN_APP_CONFIRM -> MaterialTheme.colorScheme.tertiaryContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = modifier.testTag("decision_tier_badge"),
        shape = RoundedCornerShape(6.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (tier) {
                    AuthTier.HARDWARE_CONFIRM -> Icons.Default.CheckCircle
                    AuthTier.IN_APP_CONFIRM -> Icons.Default.Info
                    null -> Icons.Default.Error
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
