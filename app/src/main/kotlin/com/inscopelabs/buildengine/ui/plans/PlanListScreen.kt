package com.inscopelabs.buildengine.ui.plans

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
 */
@Composable
fun PlanListScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.plans_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.plan_list_placeholder),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
