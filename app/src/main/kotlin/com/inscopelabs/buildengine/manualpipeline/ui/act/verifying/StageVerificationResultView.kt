package com.inscopelabs.buildengine.manualpipeline.ui.act.verifying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActVerifyingState
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun StageVerificationResultView(
    state: ActVerifyingState,
    onContinueToOutcome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_verifying_result_view",
    ) {
        SectionHeader(
            title = "Gateway A Read-Back Verification (MP-007)",
            subtitle = "Inspecting live head & GET /audit/{ref}",
            icon = Icons.Default.FactCheck,
        )

        if (state.isVerifying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = "Reading back live branch head and audit trail...",
                style = MaterialTheme.typography.bodySmall,
            )
            return@ManualPipelineCard
        }

        val report = state.verificationReport
        if (report != null) {
            Text(
                text = report.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (report.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            report.liveHeadSha?.let { head ->
                HeadShaDisplay(
                    headSha = head,
                    label = "Verified live branch head (40 hex)",
                    testTag = "mp_verified_head_sha",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Observed gateway task status: ${report.observedTaskStatus?.wireValue ?: "n/a"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Audit ref: ${report.auditEntry?.auditRef ?: state.auditRef ?: "none"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onContinueToOutcome,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("mp_verify_continue_outcome"),
            ) {
                Text("View Operation Outcome")
            }
        }
    }
}
