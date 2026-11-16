package com.inscopelabs.buildengine.manualpipeline.ui.act.outcome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.inscopelabs.buildengine.manualpipeline.domain.error.ErrorPresentation
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun FailureCategoryDisplay(
    presentation: ErrorPresentation,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_failure_category_display",
        isErrorTone = true,
    ) {
        SectionHeader(
            title = presentation.title,
            subtitle = "FailureCategory: ${presentation.category.name}",
            icon = Icons.Default.Info,
        )
        Text(
            text = presentation.message,
            style = MaterialTheme.typography.bodySmall,
        )
        presentation.gatewayCode?.let { code ->
            Text(
                text = "Gateway code: $code",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        presentation.suggestedAction?.let { action ->
            Text(
                text = "Next step: $action",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
