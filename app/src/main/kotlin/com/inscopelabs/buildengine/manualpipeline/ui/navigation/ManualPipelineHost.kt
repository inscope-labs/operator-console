package com.inscopelabs.buildengine.manualpipeline.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inscopelabs.buildengine.manualpipeline.ui.canvas.CanvasHost
import com.inscopelabs.buildengine.manualpipeline.ui.theme.ManualPipelineTheme

@Composable
fun ManualPipelineHost(
    initialTaskId: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManualPipelineTheme {
        CanvasHost(
            initialTaskId = initialTaskId,
            onBack = onBack,
            modifier = modifier,
        )
    }
}
