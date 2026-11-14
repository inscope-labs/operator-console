package com.inscopelabs.buildengine.ui.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.core.security.AuthTier

/**
 * Design review §12: publish is the only action that crosses to the public repository.
 * The screen shows the operation, plan, task and order, the branch, the full 40-character
 * head, the environment, the confirmation tier, the capability lifetime and the guard
 * summary before anything is signed (T3-301).
 */
@Composable
fun PublishConfirmScreen(
    modifier: Modifier = Modifier,
    viewModel: PublishViewModel = hiltViewModel(),
    onSubmitted: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.publish_confirm_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        when (val current = state) {
            PublishUiState.Idle, PublishUiState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.publish_confirm_loading))
            }
            is PublishUiState.LoadFailed -> {
                Text(current.message, modifier = Modifier.testTag("publish_load_failed"))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.publish_confirm_retry)) }
            }
            is PublishUiState.Ready -> ReadyContent(
                state = current,
                onTypedHeadChanged = viewModel::onTypedHeadChanged,
                onConfirm = {
                    viewModel.confirm()
                    onSubmitted()
                },
            )
            is PublishUiState.Submitting -> SummaryBlock(current.summary)
            is PublishUiState.Finished -> SummaryBlock(current.summary)
        }
    }
}

@Composable
private fun ReadyContent(
    state: PublishUiState.Ready,
    onTypedHeadChanged: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val summary = state.summary
    SummaryBlock(summary)
    if (summary.tier == AuthTier.IN_APP_CONFIRM) {
        OutlinedTextField(
            value = state.typedHead,
            onValueChange = onTypedHeadChanged,
            label = {
                Text(stringResource(R.string.publish_confirm_typed_head, PublishSummary.TYPED_HEAD_LENGTH))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("publish_typed_head"),
        )
    }
    val typedMatches = summary.tier != AuthTier.IN_APP_CONFIRM ||
        state.typedHead.trim().equals(summary.typedHeadPrefix, ignoreCase = true)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onConfirm,
        enabled = summary.canConfirm && typedMatches,
        modifier = Modifier.fillMaxWidth().testTag("publish_confirm_button"),
    ) {
        Text(stringResource(R.string.publish_confirm_button))
    }
}

@Composable
private fun SummaryBlock(summary: PublishSummary) {
    LabelledValue(stringResource(R.string.publish_confirm_task), "${summary.taskTitle} (${summary.taskId})")
    LabelledValue(stringResource(R.string.publish_confirm_plan), summary.plan)
    LabelledValue(
        stringResource(R.string.publish_confirm_order),
        summary.order ?: stringResource(R.string.publish_confirm_order_unavailable),
        tag = "publish_order",
    )
    LabelledValue(stringResource(R.string.publish_confirm_branch), summary.branch, mono = true, tag = "publish_branch")
    LabelledValue(stringResource(R.string.publish_confirm_head), summary.headSha, mono = true, tag = "publish_head")
    LabelledValue(
        stringResource(R.string.publish_confirm_environment),
        summary.environment ?: stringResource(R.string.publish_confirm_environment_unknown),
    )
    LabelledValue(
        stringResource(R.string.publish_confirm_tier),
        when (summary.tier) {
            AuthTier.HARDWARE_CONFIRM -> stringResource(R.string.publish_tier_hardware)
            AuthTier.IN_APP_CONFIRM -> stringResource(R.string.publish_tier_in_app)
            null -> stringResource(R.string.publish_tier_unknown)
        },
        tag = "publish_tier",
    )
    Text(
        text = stringResource(R.string.publish_confirm_ttl, summary.ttlSeconds.toInt()),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("publish_ttl"),
    )
    Text(
        text = stringResource(R.string.publish_confirm_guards),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag("publish_guards"),
    )
}

@Composable
private fun LabelledValue(label: String, value: String, mono: Boolean = false, tag: String? = null) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
    }
}
