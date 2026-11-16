package com.inscopelabs.buildengine.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.domain.model.PlanDraftSummary

/**
 * The plan drafts on this device, and the entry point into the authoring editor (PMA Stage 1).
 *
 * Drafts are local. Nothing here has touched the repository, and discarding one undoes nothing
 * anywhere else — which is why the discard confirmation says so.
 */
@Composable
internal fun PlanDraftListSection(
    onOpenDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanAuthoringViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsState()
    val pendingOpen by viewModel.pendingOpenDraftId.collectAsState()
    var pendingDiscard by remember { mutableStateOf<PlanDraftSummary?>(null) }

    LaunchedEffect(pendingOpen) {
        val draftId = pendingOpen ?: return@LaunchedEffect
        onOpenDraft(draftId)
        viewModel.consumePendingOpen()
    }

    Card(modifier = modifier.fillMaxWidth().testTag("pma_draft_list_section")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Plan drafts",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Drafts live on this device. They are not plans until they have been " +
                            "submitted to the Build Engine and merged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { viewModel.createDraft() },
                    modifier = Modifier.testTag("pma_create_plan_button"),
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Text("  Create Plan")
                }
            }

            if (drafts.isEmpty()) {
                Text(
                    text = "No drafts yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pma_no_drafts"),
                )
            }

            drafts.forEach { draft ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pma_draft_row_${draft.draftId}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = draft.title.ifBlank { draft.planId },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "'${draft.planId}' · revision ${draft.revision} · " +
                                "${draft.phaseCount} phase(s), ${draft.stageCount} stage(s), " +
                                "${draft.taskCount} task(s)" +
                                if (draft.isPlanNumberReserved) "" else " · number not reserved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onOpenDraft(draft.draftId) },
                        modifier = Modifier.testTag("pma_open_draft_${draft.draftId}"),
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Open draft")
                    }
                    IconButton(
                        onClick = { pendingDiscard = draft },
                        modifier = Modifier.testTag("pma_discard_draft_${draft.draftId}"),
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Discard draft")
                    }
                }
            }
        }
    }

    pendingDiscard?.let { draft ->
        AlertDialog(
            modifier = Modifier.testTag("pma_discard_dialog"),
            onDismissRequest = { pendingDiscard = null },
            title = { Text("Discard this draft?") },
            text = {
                Text(
                    text = "'${draft.planId}' is removed from this device. It has not been submitted, " +
                        "so nothing needs to be undone in the repository.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.discardById(draft.draftId)
                        pendingDiscard = null
                    },
                    modifier = Modifier.testTag("pma_discard_confirm"),
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text("Keep") }
            },
        )
    }
}
