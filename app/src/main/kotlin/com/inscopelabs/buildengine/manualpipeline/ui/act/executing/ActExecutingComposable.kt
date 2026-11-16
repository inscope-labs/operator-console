package com.inscopelabs.buildengine.manualpipeline.ui.act.executing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActExecutingState

@Composable
fun ActExecutingComposable(
    state: ActExecutingState,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") viewModel: ActExecutingViewModel = hiltViewModel(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        UploadProgressView(
            stageLabel = state.stageLabel,
            branch = state.branch,
            headSha = state.headSha,
            bytesTotal = state.bytesTotal,
        )
    }
}
