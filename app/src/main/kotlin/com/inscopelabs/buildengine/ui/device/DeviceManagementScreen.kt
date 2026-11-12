package com.inscopelabs.buildengine.ui.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Device list and revocation.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 * TODO(Stage 4.4 / T4-401): list enrolments and revoke a device through the Gateway A admin routes.
 *
 * Pre-execution scaffold (docs/PRE-EXECUTION-ONBOARDING.md). Not wired into navigation, DI or any caller.
 */
@Composable
fun DeviceManagementScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Devices",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Not implemented. Do not use this screen for any operator decision.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
