package com.inscopelabs.buildengine.ui.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The Lab scenario screen.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 * TODO(Stage 5.3 / T5-301..T5-304): list and run scenarios against a test gateway only; a persistent banner 'LAB — not an operator approval' is required; release builds must contain no Lab code.
 *
 * Pre-execution scaffold (docs/PRE-EXECUTION-ONBOARDING.md). Not wired into navigation, DI or any caller.
 */
@Composable
fun LabScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Lab",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Not implemented. Do not use this screen for any operator decision.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
