package com.inscopelabs.buildengine.manualpipeline.ui.inspect.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.state.UiState

@Composable
fun TaskTab(
    initialTaskId: String?,
    modifier: Modifier = Modifier,
    viewModel: TaskTabViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialTaskId) {
        viewModel.load(initialTaskId)
    }

    val state by viewModel.uiState.collectAsState()

    when (val current = state) {
        is UiState.Idle, is UiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("mp_task_tab_loading"))
            }
        }
        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                ManualPipelineCard(isErrorTone = true, testTag = "mp_task_tab_error") {
                    Text(current.message)
                    OutlinedButton(
                        onClick = { viewModel.load(initialTaskId) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mp_task_tab_retry"),
                    ) {
                        Text("Retry Read")
                    }
                }
            }
        }
        is UiState.Success -> {
            val data = current.data
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    LifecycleDashboard(dashboard = data.dashboard)
                }
                item {
                    DiscoveryList(
                        tasks = data.tasks,
                        selectedTaskId = data.selectedTask?.id,
                        onSelectTask = viewModel::selectTask,
                    )
                }
                item {
                    EligibilityAdvisoryPanel(verdict = data.verdict)
                }
                if (data.selectedTask != null) {
                    item {
                        Button(
                            onClick = { viewModel.switchToActCanvas() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("mp_task_proceed_to_act"),
                        ) {
                            Text("Proceed to Act Canvas")
                        }
                    }
                }
            }
        }
    }
}
