package com.inscopelabs.buildengine.manualpipeline.ui.inspect.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperatorActionRecord
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader
import java.time.Instant

@Composable
fun OperatorActionHistory(
    actions: List<OperatorActionRecord>,
    onSelectAuditRef: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_operator_action_history",
    ) {
        SectionHeader(
            title = "Operator Action Log",
            subtitle = "Non-secret chronological history (MP-013)",
            icon = Icons.Default.History,
        )

        if (actions.isEmpty()) {
            Text(
                text = "No manual pipeline actions recorded yet on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManualPipelineCard
        }

        actions.forEachIndexed { idx, action ->
            if (idx > 0) HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        val ref = action.auditRef
                        if (!ref.isNullOrBlank()) {
                            base.clickable { onSelectAuditRef(ref) }
                        } else {
                            base
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${action.actionName} • ${action.outcomeLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = Instant.ofEpochMilli(action.timestampEpochMs).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = action.detail,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!action.auditRef.isNullOrBlank()) {
                    Text(
                        text = "auditRef: ${action.auditRef} (tap to verify)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (!action.headSha.isNullOrBlank()) {
                    Text(
                        text = "head: ${action.headSha}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
