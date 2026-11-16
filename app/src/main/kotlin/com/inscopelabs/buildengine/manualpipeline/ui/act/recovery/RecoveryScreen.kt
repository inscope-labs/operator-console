package com.inscopelabs.buildengine.manualpipeline.ui.act.recovery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun RecoveryScreen(
    modifier: Modifier = Modifier,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    BackHandler {
        viewModel.close()
    }

    val model by viewModel.uiModel.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mp_recovery_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ManualPipelineCard(testTag = "mp_recovery_header_card") {
                SectionHeader(
                    title = "Lookup-Only Operation Recovery",
                    subtitle = "Queries GET /operations/{id}. Never auto-resends write operations.",
                    icon = Icons.Default.SyncProblem,
                )
                model.lastSummary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::close,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("mp_recovery_close"),
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = viewModel::reconcileAll,
                        enabled = !model.isReconciling && model.pendingRecords.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("mp_recovery_reconcile_all"),
                    ) {
                        Text("Lookup All Pending")
                    }
                }
            }
        }

        if (model.pendingRecords.isEmpty()) {
            item {
                ManualPipelineCard(testTag = "mp_recovery_empty") {
                    Text(
                        text = "No uncertain or in-flight journaled operations require lookup recovery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(model.pendingRecords, key = { it.idempotencyKey }) { record ->
                val lookup = model.statusesByKey[record.idempotencyKey]
                ManualPipelineCard(testTag = "mp_recovery_item_${record.idempotencyKey}") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${record.operationName} • ${record.state}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Key: ${record.idempotencyKey}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        Text(
                            text = "Plan ${record.planId} • Task ${record.taskId} • Branch ${record.branch}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        lookup?.let { status ->
                            Text(
                                text = "Gateway Lookup: ${status.state.name} — ${status.detail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.reconcileSingle(record.idempotencyKey) },
                            enabled = !model.isReconciling,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("mp_recovery_lookup_${record.idempotencyKey}"),
                        ) {
                            Text("Lookup Status (GET /operations/{id})")
                        }
                    }
                }
            }
        }
    }
}
