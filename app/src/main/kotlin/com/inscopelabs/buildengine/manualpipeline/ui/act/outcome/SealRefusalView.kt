package com.inscopelabs.buildengine.manualpipeline.ui.act.outcome

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun SealRefusalView(
    code: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_seal_refusal_view",
        isErrorTone = true,
    ) {
        SectionHeader(
            title = "Submission Sealed (Immutable)",
            subtitle = "Code: $code",
            icon = Icons.Default.Lock,
        )
        Text(
            text = message.ifBlank {
                "Gateway A sealed this submission after upload; replacement or mutation is prohibited."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
