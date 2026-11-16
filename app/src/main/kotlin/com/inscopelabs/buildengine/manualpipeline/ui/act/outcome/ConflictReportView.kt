package com.inscopelabs.buildengine.manualpipeline.ui.act.outcome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun ConflictReportView(
    conflict: OperationOutcome.Conflict,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_conflict_report_view",
        isErrorTone = true,
    ) {
        SectionHeader(
            title = "Concurrency / Head Drift Conflict",
            subtitle = "Nothing was committed for the stale capability",
            icon = Icons.Default.SyncProblem,
        )
        Text(
            text = conflict.message,
            style = MaterialTheme.typography.bodySmall,
        )
        conflict.liveHeadSha?.takeIf { it.isNotBlank() }?.let { live ->
            HeadShaDisplay(
                headSha = live,
                label = "Reported live branch head (40 hex)",
                testTag = "mp_conflict_live_head",
            )
        }
    }
}
