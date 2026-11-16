package com.inscopelabs.buildengine.manualpipeline.ui.act.handoff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.manualpipeline.ui.component.ManualPipelineCard
import com.inscopelabs.buildengine.manualpipeline.ui.component.SectionHeader

@Composable
fun HandoffVerificationView(
    modifier: Modifier = Modifier,
    viewModel: HandoffViewModel = hiltViewModel(),
) {
    BackHandler {
        viewModel.close()
    }

    val model by viewModel.uiModel.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("mp_handoff_verification_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ManualPipelineCard(testTag = "mp_handoff_controls_card") {
                SectionHeader(
                    title = "Pre-Transfer Handoff Verification (MP-014)",
                    subtitle = "Verifies live head, upload auditRef, and transition auditRef",
                    icon = Icons.Default.FactCheck,
                )

                model.statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (model.missingItems.isEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }

                if (model.missingItems.isNotEmpty()) {
                    Text(
                        text = "Missing items: ${model.missingItems.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("mp_handoff_missing_items"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::close,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("mp_handoff_close"),
                    ) {
                        Text("Close")
                    }
                    Button(
                        onClick = viewModel::verifyHandoff,
                        enabled = !model.isVerifying,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("mp_handoff_verify_button"),
                    ) {
                        Text(if (model.isVerifying) "Verifying..." else "Assemble Bundle")
                    }
                }
            }
        }

        model.latestEvidence?.let { evidence ->
            item {
                HandoffReferencesCard(evidence = evidence)
            }
        }
    }
}
