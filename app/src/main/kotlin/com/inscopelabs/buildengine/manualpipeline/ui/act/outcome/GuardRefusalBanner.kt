package com.inscopelabs.buildengine.manualpipeline.ui.act.outcome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.inscopelabs.buildengine.manualpipeline.domain.model.GuardRefusal
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun GuardRefusalBanner(
    refusal: GuardRefusal,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_guard_refusal_banner",
        isErrorTone = true,
    ) {
        SectionHeader(
            title = "Gateway Guard Refusal (Authoritative)",
            subtitle = refusal.guardName ?: "gateway_guard",
            icon = Icons.Default.GppBad,
        )
        Text(
            text = "Verbatim Code: ${refusal.code}",
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = refusal.message,
            style = MaterialTheme.typography.bodySmall,
        )
        if (refusal.isHaltRefusal) {
            Text(
                text = "Repository halt active — inspect live status before attempting further actions.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
