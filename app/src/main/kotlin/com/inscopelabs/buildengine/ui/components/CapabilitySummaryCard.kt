package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R

/**
 * Shows exactly what the operator is about to authorise.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §5: the operation, the branch, the exact head SHA and the TTL — all
 * four must be visible before the capability is minted.
 */
@Composable
fun CapabilitySummaryCard(
    operation: String,
    branch: String,
    headSha: String,
    ttlSeconds: Long,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${stringResource(R.string.label_operation)}: $operation",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${stringResource(R.string.label_branch)}: $branch",
                style = MaterialTheme.typography.bodyMedium,
            )
            BranchShaRow(sha = headSha)
            Text(
                text = "${stringResource(R.string.label_ttl)}: ${ttlSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
