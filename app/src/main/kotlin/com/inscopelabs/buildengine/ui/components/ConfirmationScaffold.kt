package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R

/**
 * The shared scaffold for a confirmation-gated action.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5 / §13: the confirmation is the operator's, and it is what
 * authorises the capability. The biometric prompt belongs on top of this.
 */
@Composable
fun ConfirmationScaffold(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(onClick = onConfirm) { Text(text = confirmLabel) }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    }
}
