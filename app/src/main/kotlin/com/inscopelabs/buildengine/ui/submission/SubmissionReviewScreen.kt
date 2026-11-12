package com.inscopelabs.buildengine.ui.submission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Submission inspection and blocked-task review.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 * TODO(Stage 4.3 / T4-301): show hashes, expected branch SHA and blockers, and tell apart missing input, specification conflict, decision needed, policy refusal and technical failure.
 *
 * Pre-execution scaffold (docs/PRE-EXECUTION-ONBOARDING.md). Not wired into navigation, DI or any caller.
 */
@Composable
fun SubmissionReviewScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Submission review",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Not implemented. Do not use this screen for any operator decision.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
