package com.inscopelabs.buildengine.manualpipeline.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.util.HeadHex

/**
 * Displays the complete, untruncated 40-character hexadecimal Git commit SHA (I-07).
 */
@Composable
fun HeadShaDisplay(
    headSha: String,
    label: String = "Live branch head (40 hex)",
    modifier: Modifier = Modifier,
    grouped: Boolean = false,
    testTag: String = "mp_head_sha_display",
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val normalized = HeadHex.normalize(headSha)
    val displayText = when {
        normalized.isBlank() -> "Unavailable"
        grouped -> HeadHex.grouped40(normalized)
        else -> normalized
    }
    val isValid = HeadHex.isFull40(normalized)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isValid) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (isValid) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.weight(1f),
            )
            if (normalized.isNotBlank()) {
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(normalized)) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_copy"),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy full 40-character head SHA",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
