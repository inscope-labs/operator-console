package com.inscopelabs.buildengine.manualpipeline.ui.inspect.history

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
fun HistoryTab(
    modifier: Modifier = Modifier,
    viewModel: HistoryTabViewModel = hiltViewModel(),
) {
    val model by viewModel.uiModel.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OperatorActionHistory(
                actions = model.actions,
                onSelectAuditRef = viewModel::inspectAuditRef,
            )
        }
        item {
            AuditEvidenceViewer(
                entries = model.auditEntries,
                selectedAudit = model.selectedAudit,
                isLoading = model.isLoadingAudit,
                error = model.auditError,
                onRefresh = viewModel::refreshAudit,
                onSelectEntry = viewModel::inspectAuditRef,
            )
        }
    }
}
