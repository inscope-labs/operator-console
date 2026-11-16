package com.inscopelabs.buildengine.manualpipeline.ui.act.outcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.manualpipeline.domain.model.OperationOutcome
import com.inscopelabs.buildengine.manualpipeline.ui.act.states.ActOutcomeState

@Composable
fun ActOutcomeComposable(
    state: ActOutcomeState,
    modifier: Modifier = Modifier,
    viewModel: ActOutcomeViewModel = hiltViewModel(),
) {
    BackHandler {
        viewModel.done()
    }

    val needsRecovery = state.outcome is OperationOutcome.Timeout ||
        state.outcome is OperationOutcome.UncertainCompletion ||
        state.outcome is OperationOutcome.RecoveryRequired

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutcomeCard(outcome = state.outcome)
        }

        state.guardRefusal?.let { refusal ->
            item {
                if (refusal.isSealRefusal) {
                    SealRefusalView(code = refusal.code, message = refusal.message)
                } else {
                    GuardRefusalBanner(refusal = refusal)
                }
            }
        }

        if (state.outcome is OperationOutcome.Conflict) {
            item {
                ConflictReportView(conflict = state.outcome)
            }
        }

        state.errorPresentation?.let { presentation ->
            item {
                FailureCategoryDisplay(presentation = presentation)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::done,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("mp_outcome_done"),
                ) {
                    Text("Back to Actions")
                }

                if (needsRecovery) {
                    Button(
                        onClick = viewModel::openRecovery,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("mp_outcome_open_recovery"),
                    ) {
                        Text("Lookup Recovery")
                    }
                } else if (state.outcome is OperationOutcome.Succeeded) {
                    Button(
                        onClick = viewModel::openHandoff,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("mp_outcome_open_handoff"),
                    ) {
                        Text("Verify Handoff")
                    }
                }
            }
        }
    }
}
