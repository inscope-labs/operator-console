package com.inscopelabs.buildengine.manualpipeline.ui.act.idle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.model.TransitionKind
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun PermittedActionSelector(
    permittedTransitions: List<TransitionKind>,
    canTransition: Boolean,
    canUpload: Boolean,
    readOnlyReason: String?,
    onSelectTransition: (TransitionKind) -> Unit,
    onSelectUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_permitted_action_selector",
    ) {
        SectionHeader(
            title = "Permitted Write Actions",
            subtitle = "Each action requires explicit confirmation & capability minting",
        )

        if (!readOnlyReason.isNullOrBlank()) {
            Text(
                text = readOnlyReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("mp_act_blocked_reason"),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            permittedTransitions.forEach { kind ->
                OutlinedButton(
                    onClick = { onSelectTransition(kind) },
                    enabled = canTransition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("mp_action_transition_${kind.name}"),
                ) {
                    Text(kind.displayLabel)
                }
            }

            Button(
                onClick = onSelectUpload,
                enabled = canUpload,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("mp_action_upload"),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Text(" Upload Immutable Submission .zip")
            }
        }
    }
}
