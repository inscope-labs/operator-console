package com.inscopelabs.buildengine.manualpipeline.ui.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
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
import com.inscopelabs.buildengine.manualpipeline.domain.model.Environment

@Composable
fun EnvironmentBadge(
    expected: Environment,
    reported: Environment,
    modifier: Modifier = Modifier,
) {
    val matched = expected != Environment.UNPROBED &&
        reported != Environment.UNPROBED &&
        expected == reported

    val bg = when {
        matched -> MaterialTheme.colorScheme.secondaryContainer
        expected == Environment.UNPROBED || reported == Environment.UNPROBED -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val fg = when {
        matched -> MaterialTheme.colorScheme.onSecondaryContainer
        expected == Environment.UNPROBED || reported == Environment.UNPROBED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val label = if (matched) {
        reported.displayLabel
    } else {
        "${expected.displayLabel}/${reported.displayLabel}"
    }

    Surface(
        modifier = modifier.testTag("mp_environment_badge"),
        color = bg,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (matched) Icons.Default.Dns else Icons.Default.Warning,
                contentDescription = "Environment status $label",
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}
