package com.inscopelabs.buildengine.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import com.inscopelabs.buildengine.domain.model.OperatorSession
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.ErrorBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToPreflight: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val state = (uiState as? UiState.Success)?.data

    var credentialDraft by remember { mutableStateOf("") }
    var gatewayDraft by remember { mutableStateOf("") }

    LaunchedEffect(state?.notice) {
        when (state?.notice) {
            SettingsNotice.CREDENTIAL_SAVED, SettingsNotice.CREDENTIAL_CLEARED -> credentialDraft = ""
            SettingsNotice.GATEWAY_URL_SAVED, SettingsNotice.GATEWAY_URL_CLEARED -> {
                gatewayDraft = state.gatewayBaseUrl
            }
            else -> Unit
        }
    }

    LaunchedEffect(state?.gatewayBaseUrl) {
        if (gatewayDraft.isEmpty() && !state?.gatewayBaseUrl.isNullOrEmpty()) {
            gatewayDraft = state?.gatewayBaseUrl.orEmpty()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        state?.let { current ->
            GatewaySection(
                state = current,
                draft = gatewayDraft,
                onDraftChange = { gatewayDraft = it },
                onSave = { viewModel.saveGatewayBaseUrl(gatewayDraft) },
                onClear = { viewModel.clearGatewayBaseUrl() },
                onTestConnection = { viewModel.testConnection() },
                onSetExpectedEnvironment = { viewModel.saveExpectedEnvironment(it) },
                onNavigateToPreflight = onNavigateToPreflight,
            )
            CredentialSection(
                state = current,
                draft = credentialDraft,
                onDraftChange = { credentialDraft = it },
                onSave = { viewModel.saveCredential(credentialDraft) },
                onClear = { viewModel.clearCredential() },
            )
            SessionSection(
                state = current,
                onStart = { viewModel.startSession() },
                onEnd = { viewModel.endSession() },
            )
            DiagnosticsSection(
                state = current,
                onToggleActivityLog = { viewModel.setGatewayActivityLogEnabled(it) },
                onClearActivityLog = { viewModel.clearGatewayActivityLog() },
                exportText = { viewModel.gatewayActivityExportText() },
            )

            state.environmentMismatchMessage?.let { mismatchMsg ->
                ErrorBanner(message = mismatchMsg)
            }
            state.customError?.let { customMessage ->
                ErrorBanner(message = customMessage)
            } ?: state.error?.let { error ->
                ErrorBanner(message = stringResource(error.messageRes))
            }
            state.healthError?.let { message ->
                ErrorBanner(message = message)
            }
            state.notice?.let { notice ->
                Text(
                    text = stringResource(notice.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun GatewaySection(
    state: SettingsState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onTestConnection: () -> Unit,
    onSetExpectedEnvironment: (String) -> Unit,
    onNavigateToPreflight: () -> Unit = {},
) {
    SettingsCard(title = stringResource(R.string.settings_gateway_heading)) {
        LabelAndValue(
            label = stringResource(R.string.settings_gateway_status_label),
            value = stringResource(
                if (state.gatewayConfigured) {
                    R.string.settings_gateway_configured
                } else {
                    R.string.settings_gateway_not_configured
                },
            ),
        )
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text(stringResource(R.string.settings_gateway_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_gateway_url_input"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            placeholder = {
                Text(stringResource(R.string.settings_gateway_url_placeholder))
            },
        )
        Text(
            text = stringResource(R.string.settings_gateway_note),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.testTag("settings_gateway_save_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = onClear,
                enabled = state.gatewayBaseUrl.isNotEmpty(),
                modifier = Modifier.testTag("settings_gateway_clear_button"),
            ) {
                Text(stringResource(R.string.action_clear))
            }
        }

        // Expected Environment selector (T2-201)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.settings_expected_env_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_expected_env_selector"),
            ) {
                listOf("test", "staging", "production").forEach { env ->
                    val isSelected = state.expectedEnvironment.equals(env, ignoreCase = true)
                    if (isSelected) {
                        androidx.compose.material3.Button(
                            onClick = { onSetExpectedEnvironment(env) },
                            modifier = Modifier.testTag("settings_env_${env}_selected"),
                        ) {
                            Text(env)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSetExpectedEnvironment(env) },
                            modifier = Modifier.testTag("settings_env_${env}_button"),
                        ) {
                            Text(env)
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_expected_env_help),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.environmentMismatch && state.environmentMismatchMessage != null) {
            ErrorBanner(message = state.environmentMismatchMessage)
        }

        OutlinedButton(
            onClick = onTestConnection,
            enabled = state.gatewayConfigured && !state.healthTesting,
            modifier = Modifier.fillMaxWidth().testTag("settings_test_connection_button"),
        ) {
            if (state.healthTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.settings_test_connection))
            }
        }

        OutlinedButton(
            onClick = onNavigateToPreflight,
            modifier = Modifier.fillMaxWidth().testTag("settings_preflight_button"),
        ) {
            Text(stringResource(R.string.settings_run_preflight))
        }

        state.healthSnapshot?.let { snapshot ->
            HealthSnapshotBlock(snapshot = snapshot)
        }
    }
}

@Composable
private fun HealthSnapshotBlock(snapshot: GatewayHealthSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.settings_health_heading),
            style = MaterialTheme.typography.titleSmall,
        )
        LabelAndValue(
            label = stringResource(R.string.settings_health_environment),
            value = snapshot.environment.ifEmpty { "—" },
        )
        LabelAndValue(
            label = stringResource(R.string.settings_health_version),
            value = snapshot.version.ifEmpty { "—" },
        )
        LabelAndValue(
            label = stringResource(R.string.settings_health_timestamp),
            value = formatHealthTimestamp(snapshot.timestampMillis),
        )
        if (!snapshot.isTestEnvironment) {
            Text(
                text = stringResource(R.string.settings_health_non_test_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CredentialSection(
    state: SettingsState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    SettingsCard(title = stringResource(R.string.settings_credential_heading)) {
        LabelAndValue(
            label = "${stringResource(R.string.settings_credential_state_label)} (${state.expectedEnvironment})",
            value = stringResource(
                if (state.credentialConfigured) {
                    R.string.settings_credential_configured
                } else {
                    R.string.settings_credential_absent
                }
            ),
        )
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text(stringResource(R.string.settings_credential_field_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("settings_credential_input"),
        )
        Text(
            text = stringResource(R.string.settings_credential_help),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onSave,
                enabled = draft.isNotBlank(),
                modifier = Modifier.testTag("settings_credential_save_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = onClear,
                enabled = state.credentialConfigured,
                modifier = Modifier.testTag("settings_credential_clear_button"),
            ) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}

@Composable
private fun SessionSection(
    state: SettingsState,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) {
    SettingsCard(title = stringResource(R.string.settings_session_heading)) {
        LabelAndValue(
            label = stringResource(R.string.settings_session_state_label),
            value = state.session?.let { session ->
                stringResource(R.string.settings_session_active, formatSessionStart(session))
            } ?: stringResource(R.string.settings_session_absent),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.session == null) {
                OutlinedButton(onClick = onStart) {
                    Text(stringResource(R.string.settings_session_start_action))
                }
            } else {
                TextButton(onClick = onEnd) {
                    Text(stringResource(R.string.settings_session_end_action))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LabelAndValue(label: String, value: String, monospace: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}


@Composable
private fun DiagnosticsSection(
    state: SettingsState,
    onToggleActivityLog: (Boolean) -> Unit,
    onClearActivityLog: () -> Unit,
    exportText: () -> String,
) {
    val context = LocalContext.current
    SettingsCard(title = stringResource(R.string.settings_diagnostics_heading)) {
        Text(
            text = stringResource(R.string.settings_diagnostics_help),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_gateway_activity_log),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.gatewayActivityLogEnabled,
                onCheckedChange = onToggleActivityLog,
            )
        }
        if (state.gatewayActivityLogEnabled) {
            if (state.gatewayActivityEvents.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_gateway_activity_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.gatewayActivityEvents.take(20).forEach { event ->
                    Text(
                        text = event.summaryLine(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        val text = exportText()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_gateway_activity_share_subject))
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, context.getString(R.string.settings_gateway_activity_share_title)),
                        )
                    },
                    enabled = state.gatewayActivityEvents.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.settings_gateway_activity_share))
                }
                TextButton(
                    onClick = onClearActivityLog,
                    enabled = state.gatewayActivityEvents.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.settings_gateway_activity_clear))
                }
            }
        }
    }
}

private fun formatSessionStart(session: OperatorSession): String =
    Instant.ofEpochSecond(session.startedAtEpochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))

private fun formatHealthTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
}

private val SettingsNotice.messageRes: Int
    get() = when (this) {
        SettingsNotice.CREDENTIAL_SAVED -> R.string.settings_credential_saved
        SettingsNotice.CREDENTIAL_CLEARED -> R.string.settings_credential_cleared
        SettingsNotice.GATEWAY_URL_SAVED -> R.string.settings_gateway_url_saved
        SettingsNotice.GATEWAY_URL_CLEARED -> R.string.settings_gateway_url_cleared
        SettingsNotice.EXPECTED_ENV_SAVED -> R.string.settings_expected_env_saved
        SettingsNotice.HEALTH_OK -> R.string.settings_health_ok
        SettingsNotice.SESSION_STARTED -> R.string.settings_session_started
        SettingsNotice.SESSION_ENDED -> R.string.settings_session_ended
    }

private val SettingsError.messageRes: Int
    get() = when (this) {
        SettingsError.CREDENTIAL_REJECTED -> R.string.settings_credential_rejected
        SettingsError.CREDENTIAL_STORAGE_UNAVAILABLE -> R.string.settings_credential_storage_unavailable
        SettingsError.GATEWAY_URL_INVALID -> R.string.settings_gateway_url_invalid
    }
