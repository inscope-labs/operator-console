package com.inscopelabs.buildengine.manualpipeline.ui.act.confirming

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActActionKind
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActConfirmingState

@Composable
fun ActConfirmingComposable(
    state: ActConfirmingState,
    modifier: Modifier = Modifier,
    viewModel: ActConfirmingViewModel = hiltViewModel(),
) {
    BackHandler {
        viewModel.cancelConfirmation()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        when (state.actionKind) {
            ActActionKind.TRANSITION -> TransitionConfirmationDialog(
                state = state,
                onTypedHeadChange = viewModel::updateTypedHead,
                onConfirm = viewModel::confirmAndExecute,
                onCancel = viewModel::cancelConfirmation,
            )
            ActActionKind.UPLOAD -> UploadConfirmationDialog(
                state = state,
                onTypedHeadChange = viewModel::updateTypedHead,
                onConfirm = viewModel::confirmAndExecute,
                onCancel = viewModel::cancelConfirmation,
            )
        }
    }
}
