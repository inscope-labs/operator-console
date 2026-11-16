package com.inscopelabs.buildengine.manualpipeline.ui.act.executing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard

@Composable
fun UploadProgressView(
    stageLabel: String,
    branch: String,
    headSha: String,
    bytesTotal: Long?,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_executing_progress_view",
    ) {
        Text(
            text = stageLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("mp_executing_progress_bar"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Target branch: $branch",
                style = MaterialTheme.typography.bodySmall,
            )
            if (bytesTotal != null) {
                Text(
                    text = "Immutable payload size: $bytesTotal bytes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Journal intent persisted atomically prior to Gateway A dispatch. Keep screen open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HeadShaDisplay(
            headSha = headSha,
            label = "Bound commit head (40 hex)",
            testTag = "mp_executing_head",
        )
    }
}
