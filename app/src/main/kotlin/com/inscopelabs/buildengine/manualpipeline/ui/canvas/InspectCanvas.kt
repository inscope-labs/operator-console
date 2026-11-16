package com.inscopelabs.buildengine.manualpipeline.ui.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.inscopelabs.buildengine.manualpipeline.ui.inspect.artifacts.ArtifactsTab
import com.inscopelabs.buildengine.manualpipeline.ui.inspect.history.HistoryTab
import com.inscopelabs.buildengine.manualpipeline.ui.inspect.task.TaskTab
import com.inscopelabs.buildengine.manualpipeline.ui.state.InspectTab

@Composable
fun InspectCanvas(
    activeTab: InspectTab,
    initialTaskId: String?,
    onSelectTab: (InspectTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("mp_inspect_canvas"),
    ) {
        PrimaryTabRow(selectedTabIndex = activeTab.ordinal) {
            InspectTab.entries.forEach { tab ->
                Tab(
                    selected = tab == activeTab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(tab.label) },
                    modifier = Modifier.testTag("mp_inspect_tab_${tab.name}"),
                )
            }
        }

        when (activeTab) {
            InspectTab.TASK -> TaskTab(initialTaskId = initialTaskId)
            InspectTab.ARTIFACTS -> ArtifactsTab()
            InspectTab.HISTORY -> HistoryTab()
        }
    }
}
