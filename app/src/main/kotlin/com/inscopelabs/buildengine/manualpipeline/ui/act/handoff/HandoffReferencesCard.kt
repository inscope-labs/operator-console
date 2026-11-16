package com.inscopelabs.buildengine.manualpipeline.ui.act.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.model.HandoffEvidence
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun HandoffReferencesCard(
    evidence: HandoffEvidence,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_handoff_references_card",
    ) {
        SectionHeader(
            title = evidence.readinessState,
            subtitle = "Non-secret verification bundle (${evidence.bundleId.take(8)})",
            icon = Icons.Default.Verified,
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Plan ${evidence.planId} • Task ${evidence.taskId} • Order ${evidence.orderId}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Branch: ${evidence.branch} • Status: ${evidence.observedTaskStatus.wireValue}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = "Submission: ${evidence.submissionId}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = "zipSha256: ${evidence.zipSha256}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = "Upload auditRef: ${evidence.uploadAuditRef}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Transition auditRef: ${evidence.transitionAuditRef}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        HeadShaDisplay(
            headSha = evidence.verifiedHeadSha,
            label = "Verified handoff commit head (40 hex)",
            testTag = "mp_handoff_head_sha",
        )
    }
}
