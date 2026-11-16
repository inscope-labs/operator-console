package com.inscopelabs.buildengine.manualpipeline.ui.act.confirming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.model.ConfirmationTier
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActConfirmingState
import com.inscopelabs.buildengine.manualpipeline.ui.component.ConfirmationSurface
import com.inscopelabs.buildengine.manualpipeline.ui.component.DigestRow
import com.inscopelabs.buildengine.manualpipeline.ui.component.HeadShaDisplay

@Composable
fun UploadConfirmationDialog(
    state: ActConfirmingState,
    onTypedHeadChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manifest = state.manifest
    ConfirmationSurface(
        title = "Confirm Immutable Upload",
        subtitle = "POST /tasks/${state.task.id}/upload • ${state.expectedEnvironment.displayLabel}",
        modifier = modifier,
        testTag = "mp_upload_confirm_surface",
    ) {
        Text(
            text = "Plan: ${state.task.planId}  •  Order: ${state.task.orderId}\nSubmission: ${manifest?.submissionId.orEmpty()}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        HeadShaDisplay(
            headSha = state.task.headSha,
            label = "Upload bound to live head (40 hex)",
            testTag = "mp_confirm_upload_head",
        )
        manifest?.let {
            DigestRow(
                path = it.sourceDisplayName.ifBlank { "submission.zip" },
                sha256 = it.digest.zipSha256,
                sizeBytes = it.digest.totalSizeBytes,
                testTag = "mp_confirm_upload_digest",
            )
        }
        Text(
            text = "Warning: Uploaded submission archives become immutable once accepted by Gateway A.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        if (state.confirmationTier == ConfirmationTier.IN_APP_CONFIRM) {
            InAppConfirmationInput(
                requiredPrefix = state.requiredHeadPrefix,
                value = state.typedHeadInput,
                error = state.validationError,
                onValueChange = onTypedHeadChange,
            )
        } else {
            HardwareConfirmationPrompt(tierReason = state.tierReason)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mp_confirm_upload_cancel"),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onConfirm,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mp_confirm_execute_upload"),
            ) {
                Text("Confirm & Upload")
            }
        }
    }
}
