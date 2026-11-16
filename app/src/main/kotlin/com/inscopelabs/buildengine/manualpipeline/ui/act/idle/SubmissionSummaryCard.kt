package com.inscopelabs.buildengine.manualpipeline.ui.act.idle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.model.ManualTask
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest
import com.inscopelabs.buildengine.manualpipeline.ui.component.DigestRow
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader
import com.inscopelabs.buildengine.manualpipeline.ui.component.StatusChip

@Composable
fun SubmissionSummaryCard(
    task: ManualTask?,
    manifest: SubmissionManifest?,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_act_submission_summary",
    ) {
        SectionHeader(
            title = "Prepared Target & Submission",
            subtitle = "Verified against live Gateway A snapshot",
            icon = Icons.Default.Inventory2,
        )

        if (task == null) {
            Text(
                text = "No task selected. Choose a task in the Inspect canvas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManualPipelineCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = task.title.ifBlank { task.id },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            StatusChip(status = task.status)
        }

        Text(
            text = "Plan ${task.planId}  •  Order ${task.orderId.ifBlank { "MISSING" }}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HeadShaDisplay(
            headSha = task.headSha,
            label = "Target head SHA (40 hex)",
            testTag = "mp_act_summary_head",
        )

        if (manifest != null) {
            DigestRow(
                path = manifest.sourceDisplayName.ifBlank { "${manifest.submissionId}.zip" },
                sha256 = manifest.digest.zipSha256,
                sizeBytes = manifest.digest.totalSizeBytes,
                testTag = "mp_act_manifest_digest",
            )
        } else {
            Text(
                text = "No submission .zip attached yet (upload action disabled until attached in Artifacts).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
