package com.inscopelabs.buildengine.ui.audit

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.ErrorBanner

/**
 * One entry in the gateway's audit journal.
 *
 * docs/gateway-contract.md §3.20 — `GET /audit` and `GET /audit/{ref}`.
 *
 * Every field is a string because the contract defines it as one. The contract publishes
 * the audit response as an example only: `docs/schemas/` contains no `audit.schema.json`,
 * so this shape is taken from §3.20 and the stage must confirm it against what the
 * gateway actually returns before it is trusted (PR3-003, contract-to-client traceability).
 *
 * The record carries no capability, no signature and no nonce. The journal records what
 * was done and which key did it; the artefact that authorised it is single-use and
 * deliberately not retained (I-04).
 */
data class AuditRecord(
    val auditRef: String,
    val timestampIso: String,
    val operation: String,
    val plan: String,
    val task: String,
    val branch: String,
    val headSha: String,
    /** The device key that made the operation. The key id, never the key material. */
    val kid: String,
    /** The confirmation tier the gateway recorded. Empty when the gateway recorded none. */
    val authTier: String,
)

/**
 * The audit journal, and the record the operator has resolved.
 *
 * `resolved` is the record fetched by reference from `GET /audit/{ref}`. It is separate
 * from the list because resolving a reference is a different read from listing the
 * journal, and the operator's two questions — "what happened" and "what did this
 * reference say" — have different answers.
 */
data class AuditInspectionData(
    val records: List<AuditRecord> = emptyList(),
    val resolved: AuditRecord? = null,
    val resolveError: String? = null,
    val isResolving: Boolean = false,
)

/**
 * Inspection of audit records (Stage 4.4 / T4-401).
 *
 * ## Where the records come from
 *
 * The contract defines two audit reads — §3.20 `GET /audit` for the journal and
 * `GET /audit/{ref}` for one record. Both are bearer-authenticated reads and neither
 * needs a capability. This screen uses those two through [AuditInspectionViewModel] (G-07).
 *
 * ## Boundaries this screen keeps
 *
 * - It is read-only. It revokes nothing and resolves references strictly against the gateway.
 * - All rendered values are redacted via `Redactor` in the ViewModel and never include
 *   a capability, signature, or nonce (I-04).
 * - A reference that does not resolve is reported as not found rather than matched loosely.
 */
@Composable
fun AuditInspectionScreen(
    modifier: Modifier = Modifier,
    viewModel: AuditInspectionViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        if (viewModel.uiState.value is UiState.Idle) {
            viewModel.load()
        }
    }
    AuditInspectionScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onRefresh = { viewModel.load() },
        onResolve = { auditRef -> viewModel.resolve(auditRef) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditInspectionScreen(
    state: UiState<AuditInspectionData>,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onResolve: (auditRef: String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("audit_inspection_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Audit records") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("audit_inspection_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (state) {
            is UiState.Idle -> AuditIdleContent(
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
            )

            is UiState.Loading -> Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).testTag("audit_inspection_loading"),
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
                modifier = Modifier.padding(innerPadding).testTag("audit_inspection_error"),
            )

            is UiState.Success -> AuditInspectionContent(
                data = state.data,
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
                onResolve = onResolve,
            )
        }
    }
}

@Composable
private fun AuditIdleContent(
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
            modifier = Modifier.testTag("audit_inspection_idle"),
        )
        Text(
            text = "The audit journal is read from the gateway on demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("audit_inspection_load_button"),
        ) {
            Text("Load journal")
        }
    }
}

@Composable
private fun AuditInspectionContent(
    data: AuditInspectionData,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onResolve: (auditRef: String) -> Unit,
) {
    var referenceInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("audit_inspection_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ResolveReferenceCard(
                referenceInput = referenceInput,
                isResolving = data.isResolving,
                onReferenceInputChange = { referenceInput = it },
                onResolve = { value -> onResolve(value) },
                onRefresh = onRefresh,
            )
        }

        if (data.isResolving) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("audit_resolve_loading"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Resolving auditRef against gateway…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        data.resolveError?.takeIf { it.isNotBlank() }?.let { resolveError ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("audit_resolve_error"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = resolveError,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        data.resolved?.let { resolved ->
            item {
                ResolvedRecordCard(record = resolved)
            }
        }

        item {
            Text(
                text = "Journal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (data.records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("audit_journal_empty"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(
                        text = "The audit journal is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            items(data.records, key = { it.auditRef.ifBlank { it.timestampIso } }) { record ->
                AuditRecordRow(
                    record = record,
                    onResolve = onResolve,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Resolving a reference.
 *
 * The reference is resolved by the gateway, not by searching the local list: the console
 * may be holding a stale or partial journal, and a reference the operator was given in
 * a message has to resolve against the journal of record.
 */
@Composable
private fun ResolveReferenceCard(
    referenceInput: String,
    isResolving: Boolean,
    modifier: Modifier = Modifier,
    onReferenceInputChange: (String) -> Unit,
    onResolve: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val trimmed = referenceInput.trim()

    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("audit_resolve_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Resolve a reference",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = referenceInput,
                onValueChange = onReferenceInputChange,
                label = { Text("auditRef") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audit_reference_field"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onResolve(trimmed) },
                    enabled = trimmed.isNotBlank() && !isResolving,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audit_resolve_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Resolve")
                }
                TextButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audit_journal_refresh_button"),
                ) {
                    Text("Reload journal")
                }
            }

            Text(
                text = "The reference is resolved against the gateway, not against the " +
                    "records currently on screen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The record a reference resolved to.
 */
@Composable
private fun ResolvedRecordCard(
    record: AuditRecord,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("audit_resolved_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Resolved record",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AuditRecordFields(record = record)
        }
    }
}

/**
 * One row of the journal.
 */
@Composable
private fun AuditRecordRow(
    record: AuditRecord,
    modifier: Modifier = Modifier,
    onResolve: (auditRef: String) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("audit_row_${record.auditRef}"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = record.operation.trim().ifBlank { "unknown operation" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = record.timestampIso.ifBlank { "unknown time" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = record.auditRef.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (record.plan.isNotBlank() || record.task.isNotBlank()) {
                Text(
                    text = "Plan: ${record.plan.ifBlank { "—" }} · Task: ${record.task.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (record.auditRef.isNotBlank()) {
                TextButton(
                    onClick = { onResolve(record.auditRef) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audit_row_resolve_${record.auditRef}"),
                ) {
                    Text("Inspect")
                }
            }
        }
    }
}

/**
 * Every field the journal holds, in the journal's own order.
 *
 * Nothing is hidden and nothing is substituted: a field the gateway did not record is
 * shown as an em dash, so an operator can tell "not recorded" from "not applicable".
 */
@Composable
private fun AuditRecordFields(
    record: AuditRecord,
    modifier: Modifier = Modifier,
) {
    val fields = listOf(
        "auditRef" to record.auditRef,
        "timestamp" to record.timestampIso,
        "operation" to record.operation,
        "plan" to record.plan,
        "task" to record.task,
        "branch" to record.branch,
        "headSha" to record.headSha,
        "kid" to record.kid,
        "authTier" to record.authTier,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        fields.forEach { (name, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(width = 88.dp, height = 20.dp),
                )
                Text(
                    text = value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Text(
                text = "The journal records what was done and which key did it. It holds no " +
                    "capability, no signature and no nonce.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
