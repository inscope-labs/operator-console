package com.inscopelabs.buildengine.manualpipeline.ui.inspect.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.BuildLifecycleDashboardUseCase
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun LifecycleDashboard(
    dashboard: BuildLifecycleDashboardUseCase.LifecycleDashboard,
    modifier: Modifier = Modifier,
) {
    ManualPipelineCard(
        modifier = modifier,
        testTag = "mp_lifecycle_dashboard",
    ) {
        SectionHeader(
            title = "Pipeline Lifecycle Snapshot",
            subtitle = "Gateway plan counts and display stages",
            icon = Icons.Default.Dashboard,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricPill("Ready", dashboard.readyCount, Modifier.weight(1f))
            MetricPill("In Prog", dashboard.inProgressCount, Modifier.weight(1f))
            MetricPill("Done", dashboard.completedCount, Modifier.weight(1f))
            MetricPill("Blocked", dashboard.blockedCount, Modifier.weight(1f))
        }

        if (dashboard.stagesByPlan.isNotEmpty()) {
            dashboard.stagesByPlan.forEach { (planId, stages) ->
                if (stages.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Plan $planId stages (display only)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        stages.forEach { stage ->
                            Text(
                                text = "• ${stage.title} (${stage.milestone})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
