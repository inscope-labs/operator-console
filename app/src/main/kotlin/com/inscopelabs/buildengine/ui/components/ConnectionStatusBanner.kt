package com.inscopelabs.buildengine.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.model.ConnectionStatus

/**
 * Shell connection status banner.
 *
 * Always visible so the operator knows whether the console is talking to a gateway.
 * Tapping navigates to Settings (deep link for configuration / Test connection).
 */
@Composable
fun ConnectionStatusBanner(
    status: ConnectionStatus,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (container, content, icon, label, tag) = when (status) {
        is ConnectionStatus.NotConfigured -> BannerVisual(
            container = MaterialTheme.colorScheme.surfaceVariant,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.CloudOff,
            label = stringResource(R.string.connection_not_configured),
            tag = "connection_not_configured",
        )
        is ConnectionStatus.Unreachable -> BannerVisual(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Default.Warning,
            label = stringResource(R.string.connection_unreachable),
            tag = "connection_unreachable",
        )
        is ConnectionStatus.Connected -> {
            val envLabel = when {
                status.environment.equals("test", ignoreCase = true) ->
                    stringResource(R.string.connection_connected_test)
                status.environment.equals("staging", ignoreCase = true) ->
                    stringResource(R.string.connection_connected_staging)
                status.environment.equals("production", ignoreCase = true) ->
                    stringResource(R.string.connection_connected_production)
                else ->
                    stringResource(R.string.connection_connected_other, status.environment)
            }
            BannerVisual(
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Default.CloudDone,
                label = envLabel,
                tag = "connection_connected",
            )
        }
    }

    Surface(
        color = container,
        contentColor = content,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_title),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private data class BannerVisual(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val tag: String,
)
