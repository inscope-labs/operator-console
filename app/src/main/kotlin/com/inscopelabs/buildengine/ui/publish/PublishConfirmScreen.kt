package com.inscopelabs.buildengine.ui.publish

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
 * Design review §12: publish is the only action that crosses to the public
 * repository. The screen must show the branch, the exact head SHA and the guard
 * summary before anything is signed.
 */
@Composable
fun PublishConfirmScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.publish_confirm_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.publish_confirm_placeholder),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
