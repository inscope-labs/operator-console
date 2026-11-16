package com.inscopelabs.buildengine.manualpipeline.ui.chrome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun OptionsMenu(
    expanded: Boolean,
    pendingRecoveryCount: Int,
    onDismiss: () -> Unit,
    onOpenRecovery: () -> Unit,
    onOpenHandoff: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("mp_options_menu"),
    ) {
        DropdownMenuItem(
            text = {
                val label = if (pendingRecoveryCount > 0) {
                    "Operation Recovery ($pendingRecoveryCount)"
                } else {
                    "Operation Recovery"
                }
                Text(label)
            },
            leadingIcon = {
                Icon(Icons.Default.SyncProblem, contentDescription = null)
            },
            onClick = onOpenRecovery,
            modifier = Modifier.testTag("mp_menu_recovery"),
        )
        DropdownMenuItem(
            text = { Text("Pre-Transfer Handoff Verification") },
            leadingIcon = {
                Icon(Icons.Default.FactCheck, contentDescription = null)
            },
            onClick = onOpenHandoff,
            modifier = Modifier.testTag("mp_menu_handoff"),
        )
        DropdownMenuItem(
            text = { Text("Refresh Live Heads") },
            leadingIcon = {
                Icon(Icons.Default.Refresh, contentDescription = null)
            },
            onClick = onRefresh,
            modifier = Modifier.testTag("mp_menu_refresh"),
        )
    }
}
