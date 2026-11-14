package com.inscopelabs.buildengine.ui.decision

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.usecase.write.DecideResult
import com.inscopelabs.buildengine.domain.usecase.write.FrameworkDecision
import com.inscopelabs.buildengine.ui.components.OperationResultBanner
import com.inscopelabs.buildengine.ui.publish.PublishBannerPresenter
import com.inscopelabs.buildengine.ui.publish.publishBannerMessage

/**
 * Owner framework decision screen (`POST /plans/{id}/decide`, T3-404 / PR3-025).
 *
 * Captures required attribution, rationale, scope, and evidence, and displays the active
 * PR3-003 contract-reconciliation blocker so submission stays disabled until schema/route settle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionScreen(
    modifier: Modifier = Modifier,
    viewModel: DecisionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("decision_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.decision_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.contractBlockedReason?.let { reason ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("decision_contract_blocker"),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.decision_contract_blocked_heading),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.decision_choice_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FrameworkDecision.entries.forEach { choice ->
                    FilterChip(
                        selected = state.selectedDecision == choice,
                        onClick = { viewModel.selectDecision(choice) },
                        label = { Text(choice.wireValue) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("decision_choice_${choice.name.lowercase()}"),
                    )
                }
            }

            OutlinedTextField(
                value = state.plan,
                onValueChange = viewModel::updatePlan,
                label = { Text(stringResource(R.string.decision_plan_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("decision_plan_input"),
            )
            OutlinedTextField(
                value = state.task,
                onValueChange = viewModel::updateTask,
                label = { Text(stringResource(R.string.decision_task_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("decision_task_input"),
            )
            OutlinedTextField(
                value = state.order,
                onValueChange = viewModel::updateOrder,
                label = { Text(stringResource(R.string.decision_order_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("decision_order_input"),
            )
            OutlinedTextField(
                value = state.branch,
                onValueChange = viewModel::updateBranch,
                label = { Text(stringResource(R.string.decision_branch_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("decision_branch_input"),
            )
            OutlinedTextField(
                value = state.headSha,
                onValueChange = viewModel::updateHeadSha,
                label = { Text(stringResource(R.string.decision_head_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("decision_head_input"),
            )
            OutlinedTextField(
                value = state.attribution,
                onValueChange = viewModel::updateAttribution,
                label = { Text(stringResource(R.string.decision_attribution_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("decision_attribution_input"),
            )
            OutlinedTextField(
                value = state.rationale,
                onValueChange = viewModel::updateRationale,
                label = { Text(stringResource(R.string.decision_rationale_label)) },
                modifier = Modifier.fillMaxWidth().testTag("decision_rationale_input"),
            )
            OutlinedTextField(
                value = state.scope,
                onValueChange = viewModel::updateScope,
                label = { Text(stringResource(R.string.decision_scope_label)) },
                modifier = Modifier.fillMaxWidth().testTag("decision_scope_input"),
            )
            OutlinedTextField(
                value = state.evidence,
                onValueChange = viewModel::updateEvidence,
                label = { Text(stringResource(R.string.decision_evidence_label)) },
                modifier = Modifier.fillMaxWidth().testTag("decision_evidence_input"),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.decision_notes_label)) },
                modifier = Modifier.fillMaxWidth().testTag("decision_notes_input"),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { viewModel.submitDecision() },
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("decision_confirm_button"),
            ) {
                Text(
                    if (state.isSubmitting) {
                        stringResource(R.string.decision_submitting)
                    } else {
                        stringResource(R.string.decision_confirm_button)
                    },
                )
            }

            state.lastResult?.let { result ->
                when (result) {
                    is DecideResult.ContractBlocked -> {
                        OperationResultBanner(
                            message = result.reason,
                            success = false,
                        )
                    }
                    is DecideResult.InvalidTarget -> {
                        OperationResultBanner(
                            message = result.reason,
                            success = false,
                        )
                    }
                    is DecideResult.NotAuthorized -> {
                        OperationResultBanner(
                            message = result.reason.toString(),
                            success = false,
                        )
                    }
                    is DecideResult.Executed -> {
                        val banner = PublishBannerPresenter.presentOutcome(result.outcome)
                        OperationResultBanner(
                            message = publishBannerMessage(banner),
                            success = banner.isSuccess,
                            auditRef = banner.auditRef,
                        )
                    }
                }
            }
        }
    }
}
