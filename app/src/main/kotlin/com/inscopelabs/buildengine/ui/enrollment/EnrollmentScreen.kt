package com.inscopelabs.buildengine.ui.enrollment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.ui.components.SecurityPostureBadge

/** Device key enrolment and measured security posture. */
@Composable
fun EnrollmentScreen(modifier: Modifier = Modifier) {
    val viewModel: EnrollmentViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    var alias by remember { mutableStateOf("operator-console-signing-v1") }
    var oldAlias by remember { mutableStateOf("") }
    var newAlias by remember { mutableStateOf("operator-console-signing-v2") }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.enrollment_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            "A non-exportable EC P-256 key is created in Android Keystore. The private key never leaves this device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text("New device key alias") },
            singleLine = true,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.enroll(alias) },
                enabled = !state.loading && alias.isNotBlank(),
            ) { Text("Create and enrol") }
            OutlinedButton(
                onClick = { viewModel.refreshPosture(alias) },
                enabled = !state.loading && alias.isNotBlank(),
            ) { Text("Measure posture") }
        }

        state.activeKid?.let {
            Text("Active gateway key id: $it", style = MaterialTheme.typography.bodyMedium)
        }
        state.posture?.let { SecurityPostureBadge(it) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading) CircularProgressIndicator()

        Text("Key rotation", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = oldAlias,
            onValueChange = { oldAlias = it },
            label = { Text("Current active key alias") },
            singleLine = true,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = newAlias,
            onValueChange = { newAlias = it },
            label = { Text("New key alias") },
            singleLine = true,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { viewModel.rotate(oldAlias, newAlias) },
            enabled = !state.loading && oldAlias.isNotBlank() && newAlias.isNotBlank(),
        ) { Text("Rotate device key") }
        Text(
            "Rotation preserves the old local key until the gateway confirms the new key and the local active record is committed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
