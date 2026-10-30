package com.inscopelabs.buildengine.ui.decision

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R

/**
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12, decision D-e: resolving an owner decision is effectively a
 * normative change, so this screen needs its own confirmation and attribution.
 */
@Composable
fun DecisionScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.decision_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.decision_placeholder),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
