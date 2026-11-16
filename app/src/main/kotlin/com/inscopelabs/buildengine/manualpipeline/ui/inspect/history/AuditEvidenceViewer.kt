package com.inscopelabs.buildengine.manualpipeline.ui.inspect.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.port.AuditEntry
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun AuditEvidenceViewer(
    entries: List<AuditEntry>,
    selectedAudit: AuditEntry?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onSelectEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_audit_evidence_viewer",
    ) {
        SectionHeader(
            title = "Gateway A Audit Stream",
            subtitle = "From GET /audit and GET /audit/{ref}",
            icon = Icons.Default.Policy,
            trailing = {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mp_audit_refresh"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Gateway audit trail",
                    )
                }
            },
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        selectedAudit?.let { entry ->
            ManualPipelineCard(testTag = "mp_selected_audit_detail") {
                Text(
                    text = "Audit Reference: ${entry.auditRef}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Operation: ${entry.operation} • Outcome: ${entry.outcome}${entry.code?.let { " ($it)" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Plan ${entry.planId} • Task ${entry.taskId} • Branch ${entry.branch}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (entry.headSha.isNotBlank()) {
                    HeadShaDisplay(
                        headSha = entry.headSha,
                        label = "Audit commit head (40 hex)",
                        testTag = "mp_audit_head_sha",
                    )
                }
            }
        }

        if (entries.isEmpty() && !isLoading) {
            Text(
                text = "No audit records returned by Gateway A.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEachIndexed { idx, entry ->
                if (idx > 0) HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectEntry(entry.auditRef) }
                        .testTag("mp_audit_row_${entry.auditRef}"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${entry.operation} (${entry.outcome})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = entry.auditRef,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "Task ${entry.taskId} • ${entry.branch}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
