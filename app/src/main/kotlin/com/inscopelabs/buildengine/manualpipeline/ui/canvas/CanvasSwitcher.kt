package com.inscopelabs.buildengine.manualpipeline.ui.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.ui.state.CanvasMode

@Composable
fun CanvasSwitcher(
    mode: CanvasMode,
    onModeSelected: (CanvasMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mp_canvas_switcher"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == CanvasMode.INSPECT,
            onClick = { onModeSelected(CanvasMode.INSPECT) },
            label = { Text("Inspect Canvas") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("mp_switch_inspect"),
        )
        FilterChip(
            selected = mode == CanvasMode.ACT,
            onClick = { onModeSelected(CanvasMode.ACT) },
            label = { Text("Act Canvas") },
            leadingIcon = {
                Icon(Icons.Default.PlayCircle, contentDescription = null)
            },
            modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("mp_switch_act"),
        )
    }
}
