package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.model.SecurityPosture

/**
 * States the device's real security posture.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §13: the badge must distinguish a hardware-backed key from a
 * software-backed one. Claiming hardware backing that the device does not have is a
 * misrepresentation.
 */
@Composable
fun SecurityPostureBadge(
    posture: SecurityPosture,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.label_posture),
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = if (posture.hardwareBacked) {
                stringResource(R.string.posture_hardware)
            } else {
                stringResource(R.string.posture_software)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(text = posture.summary, style = MaterialTheme.typography.bodySmall)
    }
}
