package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Surfaces the outcome of a privileged operation.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12, Appendix B.3: a rejection must be legible. A refused scope
 * mismatch, a replayed capability, a drifted head and an active halt are four
 * different things and must not be collapsed into one message.
 */
@Composable
fun OperationResultBanner(
    message: String,
    success: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth().padding(16.dp),
    )
}
