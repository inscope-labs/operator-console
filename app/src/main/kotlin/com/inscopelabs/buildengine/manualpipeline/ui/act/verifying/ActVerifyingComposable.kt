package com.inscopelabs.buildengine.manualpipeline.ui.act.verifying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActVerifyingState

@Composable
fun ActVerifyingComposable(
    state: ActVerifyingState,
    modifier: Modifier = Modifier,
    viewModel: ActVerifyingViewModel = hiltViewModel(),
) {
    LaunchedEffect(state.auditRef, state.expectedHeadSha) {
        viewModel.runVerification(state)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        StageVerificationResultView(
            state = state,
            onContinueToOutcome = { viewModel.proceedToOutcome(state) },
        )
    }
}
