package com.inscopelabs.buildengine.manualpipeline.ui.act.outcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.OutcomeChip

@Composable
fun OutcomeCard(
    outcome: OperationOutcome,
    modifier: Modifier = Modifier,
) {
    val isError = outcome !is OperationOutcome.Succeeded
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_outcome_card",
        isErrorTone = isError,
    ) {
        OutcomeChip(outcome = outcome)

        when (outcome) {
            is OperationOutcome.Succeeded -> {
                Text(
                    text = "Gateway A confirmed operation and verified read-back.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "auditRef: ${outcome.auditRef}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                if (outcome.headSha.isNotBlank()) {
                    HeadShaDisplay(
                        headSha = outcome.headSha,
                        label = "Confirmed branch head (40 hex)",
                        testTag = "mp_outcome_head",
                    )
                }
            }
            is OperationOutcome.Refused -> {
                Text(
                    text = outcome.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Code: ${outcome.code}${outcome.auditRef?.let { " • auditRef: $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            is OperationOutcome.Conflict -> {
                Text(
                    text = outcome.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Code: ${outcome.code}${outcome.auditRef?.let { " • auditRef: $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
            is OperationOutcome.Timeout -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(outcome.message, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "idempotencyKey: ${outcome.idempotencyKey} (lookup-only recovery; never auto-resent)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            is OperationOutcome.UncertainCompletion -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(outcome.message, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "idempotencyKey: ${outcome.idempotencyKey}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            is OperationOutcome.RecoveryRequired -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(outcome.message, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "idempotencyKey: ${outcome.idempotencyKey}${outcome.code?.let { " • $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
        }
    }
}
