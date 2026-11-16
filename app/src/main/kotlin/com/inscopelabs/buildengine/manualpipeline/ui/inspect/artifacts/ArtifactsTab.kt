package com.inscopelabs.buildengine.manualpipeline.ui.inspect.artifacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ArtifactsTab(
    modifier: Modifier = Modifier,
    viewModel: ArtifactsTabViewModel = hiltViewModel(),
) {
    val model by viewModel.uiModel.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            WorkOrderDeliverablesView(
                workOrder = model.workOrder,
                deliverables = model.managed?.deliverables ?: model.workOrder?.deliverables.orEmpty(),
            )
        }
        item {
            DeliverableManager(
                sealedUploadedSha256 = model.sealedUploadedSha256,
                stagedEntries = model.pendingBundleEntries,
                archiveError = model.archiveError,
                onArchivePicked = viewModel::onArchivePicked,
                onDeliverablePicked = viewModel::onDeliverableFilePicked,
                onBuildZip = viewModel::buildZipFromStagedFiles,
                onClearStaged = viewModel::clearStagedFiles,
            )
        }
        item {
            ArtifactIntegrityPanel(managed = model.managed)
        }
        if (model.managed?.manifest != null && model.managed?.validation?.valid == true) {
            item {
                Button(
                    onClick = viewModel::proceedToActCanvas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("mp_artifacts_go_to_act"),
                ) {
                    Text("Review & Upload in Act Canvas")
                }
            }
        }
    }
}
