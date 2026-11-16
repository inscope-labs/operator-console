package com.inscopelabs.buildengine.manualpipeline.ui.inspect.artifacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ManageDeliverablesUseCase
import com.inscopelabs.buildengine.manualpipeline.ui.component.DigestRow
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun ArtifactIntegrityPanel(
    managed: ManageDeliverablesUseCase.ManagedDeliverables?,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_artifact_integrity_panel",
    ) {
        SectionHeader(
            title = "Artifact Integrity & Manifest (§3.10)",
            subtitle = "Pure-Kotlin SHA-256 verification before upload minting",
            icon = Icons.Default.Fingerprint,
        )

        val manifest = managed?.manifest
        if (manifest == null) {
            Text(
                text = managed?.validation?.summary ?: "Attach or pack a submission .zip to compute SHA-256 digests.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@ManualPipelineCard
        }

        Text(
            text = managed.validation.summary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (managed.validation.valid) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )

        DigestRow(
            path = manifest.sourceDisplayName.ifBlank { "submission.zip" } + " (archive)",
            sha256 = manifest.digest.zipSha256,
            sizeBytes = manifest.digest.totalSizeBytes,
            testTag = "mp_archive_zip_digest",
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            manifest.digest.fileDigests.forEach { (path, sha) ->
                DigestRow(
                    path = path,
                    sha256 = sha,
                    sizeBytes = manifest.digest.fileSizesBytes[path] ?: 0L,
                    testTag = "mp_entry_digest_$path",
                )
            }
        }
    }
}
