package com.inscopelabs.buildengine.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.model.OperatorSession
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.ErrorBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The console's settings: the gateway it talks to, the credential it presents, and
 * the session it is in.
 *
 * ### The credential field
 *
 * The operator credential is the only secret this app holds (ADR 0002), so the field
 * is masked, and the value is dropped as soon as it is stored — it is never held in
 * the screen's state, and it is never written anywhere but the encrypted store.
 *
 * The field does not "validate" a credential. The store does, and it refuses
 * anything shaped like a repository credential with a reason the operator can act
 * on. A screen that quietly accepted the wrong paste and failed later at the
 * gateway would be the worse design.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val state = (uiState as? UiState.Success)?.data

    // The typed credential lives here, in the composition, and not in the
    // ViewModel: a StateFlow is observable, loggable and restorable, and a
    // credential should be in as few of those as possible.
    var credentialDraft by remember { mutableStateOf("") }

    // The typed value is dropped once it has been stored, and once the stored
    // credential has been deleted. Leaving it in the field afterwards would leave
    // the secret sitting on screen for no reason.
    LaunchedEffect(state?.notice) {
        when (state?.notice) {
            SettingsNotice.CREDENTIAL_SAVED, SettingsNotice.CREDENTIAL_CLEARED -> credentialDraft = ""
            else -> Unit
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
            GatewaySection(current)
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

            state.error?.let { error ->
                ErrorBanner(message = stringResource(error.messageRes))
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

/** The gateway the console talks to, and the fact that it is not configurable. */
@Composable
private fun GatewaySection(state: SettingsState) {
    SettingsCard(title = stringResource(R.string.settings_gateway_heading)) {
        LabelAndValue(
            label = stringResource(R.string.settings_gateway_url_label),
            value = state.gatewayBaseUrl,
            monospace = true,
        )
        Text(
            text = stringResource(R.string.settings_gateway_note),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The operator credential: its presence, and the one field that can set it.
 *
 * The field is present whether or not a credential is stored, because replacing a
 * credential is the same act as setting one. The Clear action is offered only when
 * there is something to clear.
 */
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
            label = stringResource(R.string.settings_credential_state_label),
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
            modifier = Modifier.fillMaxWidth(),
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
            ) {
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = onClear,
                enabled = state.credentialConfigured,
            ) {
                Text(stringResource(R.string.action_clear))
            }
        }
    }
}

/**
 * The session.
 *
 * The session is in-memory only and is convenience, not the security control
 * (design review §9), so this section reports it honestly: when it opened, and
 * that ending it is one tap.
 */
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

/** A titled card. The settings screen is a list of them. */
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

/** A label above a value. */
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

/** The session's start time, in the device's locale and time zone. */
private fun formatSessionStart(session: OperatorSession): String =
    Instant.ofEpochSecond(session.startedAtEpochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))

/** The copy for a successful action. */
private val SettingsNotice.messageRes: Int
    get() = when (this) {
        SettingsNotice.CREDENTIAL_SAVED -> R.string.settings_credential_saved
        SettingsNotice.CREDENTIAL_CLEARED -> R.string.settings_credential_cleared
        SettingsNotice.SESSION_STARTED -> R.string.settings_session_started
        SettingsNotice.SESSION_ENDED -> R.string.settings_session_ended
    }

/** The copy for a failed action. */
private val SettingsError.messageRes: Int
    get() = when (this) {
        SettingsError.CREDENTIAL_REJECTED -> R.string.settings_credential_rejected
        SettingsError.CREDENTIAL_STORAGE_UNAVAILABLE -> R.string.settings_credential_storage_unavailable
    }
