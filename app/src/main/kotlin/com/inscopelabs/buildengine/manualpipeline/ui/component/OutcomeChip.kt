package com.inscopelabs.buildengine.manualpipeline.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Warning
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
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome

@Composable
fun OutcomeChip(
    outcome: OperationOutcome,
    modifier: Modifier = Modifier,
) {
    val (bg, fg, icon, label) = when (outcome) {
        is OperationOutcome.Succeeded -> OutcomeVisual(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Default.CheckCircle,
            "SUCCEEDED",
        )
        is OperationOutcome.Refused -> OutcomeVisual(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Error,
            "REFUSED (${outcome.code})",
        )
        is OperationOutcome.Conflict -> OutcomeVisual(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.SyncProblem,
            "CONFLICT (${outcome.code})",
        )
        is OperationOutcome.Timeout -> OutcomeVisual(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.HourglassTop,
            "TIMEOUT (LOOKUP)",
        )
        is OperationOutcome.UncertainCompletion -> OutcomeVisual(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Warning,
            "UNCERTAIN",
        )
        is OperationOutcome.RecoveryRequired -> OutcomeVisual(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Warning,
            "RECOVERY REQUIRED",
        )
    }

    Surface(
        modifier = modifier.testTag("mp_outcome_chip"),
        color = bg,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}

private data class OutcomeVisual<A, B, C, D>(val bg: A, val fg: B, val icon: C, val label: D)
