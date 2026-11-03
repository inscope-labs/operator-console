package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.domain.model.SecurityPosture

/**
 * Shows exactly what the operator is about to authorise.
 *
 * Design review §5: the operation, the task, the plan, the branch, the exact
 * 40-character head SHA and the TTL — all must be visible before the capability
 * is minted.
 */
@Composable
fun CapabilitySummaryCard(
    operation: String,
    branch: String,
    headSha: String,
    ttlSeconds: Long,
    modifier: Modifier = Modifier,
    task: String = "",
    plan: String = "",
    authTier: AuthTier? = null,
    posture: SecurityPosture? = null,
    typedConfirmation: String = "",
    onTypedConfirmationChange: (String) -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${stringResource(R.string.label_operation)}: $operation",
                style = MaterialTheme.typography.titleMedium,
            )
            if (task.isNotBlank()) {
                Text(
                    text = "Task: $task",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (plan.isNotBlank()) {
                Text(
                    text = "Plan: $plan",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "${stringResource(R.string.label_branch)}: $branch",
                style = MaterialTheme.typography.bodyMedium,
            )
            BranchShaRow(sha = headSha)
            Text(
                text = "Head SHA (full): $headSha",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${stringResource(R.string.label_ttl)}: ${ttlSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
            )

            // Posture / Tier badge
            if (posture != null) {
                SecurityPostureBadge(posture)
            } else if (authTier != null) {
                val tierText = when (authTier) {
                    AuthTier.HARDWARE_CONFIRM -> "Biometric/PIN confirm"
                    AuthTier.IN_APP_CONFIRM -> "In-app confirm only (no device lock)"
                }
                AssistChip(
                    onClick = {},
                    label = { Text(tierText) },
                    enabled = false,
                )
            }

            // Typed-confirm field for in_app_confirm
            if (authTier == AuthTier.IN_APP_CONFIRM) {
                val expectedPrefix = headSha.take(7)
                OutlinedTextField(
                    value = typedConfirmation,
                    onValueChange = onTypedConfirmationChange,
                    label = { Text("Confirm first 7 hex characters ($expectedPrefix)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = typedConfirmation.isNotEmpty() && !expectedPrefix.equals(typedConfirmation.trim(), ignoreCase = true),
                )
            }
        }
    }
}
