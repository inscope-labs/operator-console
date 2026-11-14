package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R

/**
 * Surfaces the outcome of a privileged operation.
 *
 * Design review §12, Appendix B.3: a rejection must be legible. A refused scope
 * mismatch, a replayed capability, a drifted head and an active halt are four
 * different things and must not be collapsed into one message; the caller supplies
 * the already-distinct [message]. The gateway's [auditRef] is shown whenever there is one.
 */
@Composable
fun OperationResultBanner(
    message: String,
    success: Boolean,
    modifier: Modifier = Modifier,
    auditRef: String? = null,
) {
    Surface(
        color = if (success) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        modifier = modifier.fillMaxWidth().testTag("operation_result_banner"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("operation_result_message"),
            )
            if (!auditRef.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.publish_audit_ref, auditRef),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp).testTag("operation_result_audit_ref"),
                )
            }
        }
    }
}
