package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.model.SecurityPosture

/** Displays measured device security properties without implying gateway verification. */
@Composable
fun SecurityPostureBadge(posture: SecurityPosture, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.label_posture), style = MaterialTheme.typography.labelLarge)
        Text(
            text = if (posture.hardwareBacked) stringResource(R.string.posture_hardware)
            else stringResource(R.string.posture_software),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(posture.securityLevel) },
                enabled = false,
            )
            AssistChip(
                onClick = {},
                label = { Text(if (posture.attested) "Key attestation present" else "No key attestation") },
                enabled = false,
            )
        }
        Text(posture.summary, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "Local measurement only; the gateway makes the enrolment decision.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
