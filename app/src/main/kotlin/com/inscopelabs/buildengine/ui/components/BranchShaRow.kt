package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R

/**
 * Renders the full 40-character head SHA, monospaced and copyable.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12: publish pushes the exact reviewed SHA, so the operator must be
 * able to read it in full — an abbreviated SHA is not enough.
 */
@Composable
fun BranchShaRow(sha: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.label_head_sha),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = sha,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}
