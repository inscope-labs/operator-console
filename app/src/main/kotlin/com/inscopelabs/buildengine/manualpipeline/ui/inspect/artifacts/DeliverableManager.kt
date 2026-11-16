package com.inscopelabs.buildengine.manualpipeline.ui.inspect.artifacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader
import com.inscopelabs.buildengine.manualpipeline.util.ZipBuilder
import com.inscopelabs.buildengine.manualpipeline.util.rememberDeliverableFilePicker
import com.inscopelabs.buildengine.manualpipeline.util.rememberManualArchivePicker

@Composable
fun DeliverableManager(
    sealedUploadedSha256: String?,
    stagedEntries: Map<String, Int>,
    archiveError: String?,
    onArchivePicked: (ZipBuilder.BuildResult) -> Unit,
    onDeliverablePicked: (String, ByteArray?) -> Unit,
    onBuildZip: () -> Unit,
    onClearStaged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launchZipPicker = rememberManualArchivePicker(context, onArchivePicked)
    val launchFilePicker = rememberDeliverableFilePicker(context, onDeliverablePicked)
    val isSealed = !sealedUploadedSha256.isNullOrBlank()

    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_deliverable_manager",
    ) {
        SectionHeader(
            title = "Submission Archive (.zip)",
            subtitle = "Zero-permission Storage Access Framework picker",
            icon = Icons.Default.FolderZip,
        )

        if (isSealed) {
            Text(
                text = "Sealed archive already uploaded (sha256:${sealedUploadedSha256}). Gateway submission is immutable once uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = launchZipPicker,
                enabled = !isSealed,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mp_pick_zip_button"),
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null)
                Text(" Pick .zip")
            }
            OutlinedButton(
                onClick = launchFilePicker,
                enabled = !isSealed,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("mp_pick_file_button"),
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Text(" Add File")
            }
        }

        if (stagedEntries.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Staged Deliverable Files (${stagedEntries.size})",
                    style = MaterialTheme.typography.labelMedium,
                )
                stagedEntries.forEach { (name, bytes) ->
                    Text(
                        text = "• $name ($bytes B)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onBuildZip,
                        enabled = !isSealed,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mp_build_zip_button"),
                    ) {
                        Text("Pack Immutable .zip")
                    }
                    TextButton(
                        onClick = onClearStaged,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        if (!archiveError.isNullOrBlank()) {
            Text(
                text = archiveError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("mp_archive_error"),
            )
        }
    }
}
