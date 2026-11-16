package com.inscopelabs.buildengine.manualpipeline.ui.act.idle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ActIdleComposable(
    modifier: Modifier = Modifier,
    viewModel: ActIdleViewModel = hiltViewModel(),
) {
    val state by viewModel.idleState.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OwnershipLeaseStrip(
                leaseLabel = state.leaseOwnerLabel,
                authority = state.authority,
            )
        }
        item {
            SubmissionSummaryCard(
                task = state.selectedTask,
                manifest = state.manifest,
            )
        }
        item {
            PermittedActionSelector(
                permittedTransitions = state.permittedTransitions,
                canTransition = state.canTransition,
                canUpload = state.canUpload,
                readOnlyReason = state.readOnlyReason,
                onSelectTransition = viewModel::startTransition,
                onSelectUpload = viewModel::startUpload,
            )
        }
    }
}
