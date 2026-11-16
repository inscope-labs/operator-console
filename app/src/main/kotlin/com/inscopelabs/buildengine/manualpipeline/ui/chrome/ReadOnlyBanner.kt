package com.inscopelabs.buildengine.manualpipeline.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.ui.state.ReadOnlyState

/**
 * Prominent banner communicating read-only posture whenever Gateway A writes are disabled
 * (environment mismatch I-11, repository halt, missing credential, or un-enrolled device key).
 */
@Composable
fun ReadOnlyBanner(
    state: ReadOnlyState,
    modifier: Modifier = Modifier,
) {
    if (!state.isReadOnly) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mp_read_only_banner"),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (state.isHalted || state.environmentMismatch) Icons.Default.Lock else Icons.Default.Shield,
                contentDescription = "Read-only mode active",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        state.environmentMismatch -> "READ-ONLY: ENVIRONMENT MISMATCH"
                        state.isHalted -> "READ-ONLY: PLAN HALTED"
                        state.notEnrolled -> "READ-ONLY: DEVICE NOT ENROLLED"
                        state.missingCredential -> "READ-ONLY: CREDENTIAL MISSING"
                        else -> "READ-ONLY MODE"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = state.reason ?: "Privileged Gateway A mutations are disabled until resolved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
