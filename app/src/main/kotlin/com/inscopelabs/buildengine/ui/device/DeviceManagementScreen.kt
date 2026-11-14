package com.inscopelabs.buildengine.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.ErrorBanner

/**
 * The confirmation string the gateway requires to revoke a key.
 *
 * gateway/docs/admin-surface.md — `POST /admin/revoke` takes `confirmation` and it must
 * equal `REVOKE <kid>`. The gateway refuses anything else.
 *
 * The console asks the operator to type it. A button that says "Revoke" is too easy to
 * press by accident, and revocation is irreversible for that key (the status becomes
 * `retired` and the key can never be used again) — so the operator has to write down
 * which key they are about to retire.
 */
fun revokeConfirmationFor(kid: String): String = "REVOKE $kid"

/**
 * A device enrolled against the gateway.
 *
 * gateway/docs/admin-surface.md — `GET /admin/enrolments` returns `kid`, `alias`,
 * `authTier`, `status` and `enrolledAt`. Public keys are deliberately not returned, and
 * this screen does not ask for them or display them.
 */
data class EnrolledDevice(
    val kid: String,
    val alias: String,
    val authTier: String,
    val status: String,
    val enrolledAtEpochSeconds: Long,
)

/**
 * The result of a revocation.
 *
 * `auditRef` is the gateway's audit reference for the revocation (the admin surface
 * records revocations). A revocation that produced no audit reference is reported as
 * such rather than being shown as a clean success.
 */
data class RevokeOutcome(
    val kid: String,
    val revoked: Boolean,
    val revokedAtEpochSeconds: Long?,
    val auditRef: String?,
)

/**
 * The enrolled devices and the outcome of the last revocation attempt.
 *
 * `localActiveKid` is this device's own key id, read from the local enrolment store.
 * It marks which row is "this device" so the operator does not retire the key they are
 * holding. It is local metadata, not an authority: the gateway's list is the authority.
 */
data class DeviceManagementData(
    val devices: List<EnrolledDevice> = emptyList(),
    val localActiveKid: String = "",
    val revokeOutcome: RevokeOutcome? = null,
)

/**
 * Device list and revocation.
 *
 * **Proposed baseline.** This is a pre-execution proposal produced against
 * `docs/PRE-EXECUTION-ONBOARDING.md`, not a completed stage. It has not been compiled,
 * has no test coverage, and is not wired into navigation, DI or any caller. The AI
 * Studio agent executes Stage 4.4, corrects this file against the gateway's admin
 * surface, adds the tests, and closes the stage only when CI is green on the pushed SHA
 * (G-04).
 *
 * TODO(Stage 4.4 / T4-401): supply the ViewModel and the two admin reads, confirm the
 * enrolment list shape against the gateway, and add the state tests the gate requires.
 *
 * ## Where the list and the revocation come from
 *
 * RELEASE-PLAN.md Stage 4.4 / T4-401: "list enrolments and revoke a device through the
 * Gateway A admin routes". The gateway's admin surface is exactly two routes
 * (gateway/docs/admin-surface.md): `GET /admin/enrolments` and `POST /admin/revoke`.
 * Both need only the operator bearer — no capability is minted for an admin read or for
 * a revocation — and there is no other admin surface, so this screen uses these two and
 * nothing else (G-07).
 *
 * ## Why the screen holds state instead of taking a ViewModel
 *
 * The screen takes a [UiState] and plain callbacks rather than a ViewModel, so this file
 * stays self-contained against what is on `main` today and leaves one seam for the
 * stage: a `@HiltViewModel` exposing `StateFlow<UiState<DeviceManagementData>>`, the
 * mapping from `GET /admin/enrolments` to [EnrolledDevice], and the `POST /admin/revoke`
 * call.
 *
 * **The signature below is a proposal.** The stage owns the final shape.
 *
 * ## Boundaries this screen keeps
 *
 * - It revokes only what the operator names explicitly, and only after the operator has
 *   typed the gateway's own confirmation string.
 * - It shows no key material. The gateway does not return public keys on the list route
 *   and this screen does not ask for them.
 * - A failed revocation is not shown as a revocation. The outcome is reported exactly as
 *   the gateway answered it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceManagementScreen(
    state: UiState<DeviceManagementData>,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onRevoke: (kid: String, reason: String) -> Unit = { _, _ -> },
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("device_management_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (state) {
            is UiState.Idle -> DeviceIdleContent(
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
            )

            is UiState.Loading -> Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).testTag("device_management_loading"),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Loading enrolments…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UiState.Error -> ErrorBanner(
                message = state.message,
                onRetry = onRefresh,
                modifier = Modifier.padding(innerPadding).testTag("device_management_error"),
            )

            is UiState.Success -> DeviceManagementContent(
                data = state.data,
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
                onRevoke = onRevoke,
            )
        }
    }
}

@Composable
private fun DeviceIdleContent(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Nothing loaded yet.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("device_management_idle"),
        )
        Text(
            text = "Enrolled devices are read from the gateway's admin surface on demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("device_management_load_button"),
        ) {
            Text("Load devices")
        }
    }
}

@Composable
private fun DeviceManagementContent(
    data: DeviceManagementData,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onRevoke: (kid: String, reason: String) -> Unit,
) {
    var pendingRevokeKid by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("device_management_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().testTag("device_management_header"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "${data.devices.size} enrolled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Read from the gateway's admin surface",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("device_management_refresh_button"),
                        ) {
                            Text("Refresh")
                        }
                    }
                }
            }
        }

        data.revokeOutcome?.let { outcome ->
            item {
                RevokeOutcomeCard(outcome = outcome)
            }
        }

        if (data.devices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("device_list_empty"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "No devices are enrolled.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "The list is the gateway's record of active enrolments. An " +
                                "empty list means the gateway holds none.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(data.devices, key = { it.kid }) { device ->
                DeviceRow(
                    device = device,
                    isLocalDevice = device.kid == data.localActiveKid && data.localActiveKid.isNotBlank(),
                    onRevoke = { pendingRevokeKid = device.kid },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    pendingRevokeKid?.let { kid ->
        RevokeConfirmationDialog(
            kid = kid,
            onDismiss = { pendingRevokeKid = null },
            onConfirm = { reason ->
                pendingRevokeKid = null
                onRevoke(kid, reason)
            },
        )
    }
}

/**
 * One enrolled device.
 */
@Composable
private fun DeviceRow(
    device: EnrolledDevice,
    isLocalDevice: Boolean,
    modifier: Modifier = Modifier,
    onRevoke: () -> Unit,
) {
    val tier = remember(device.authTier) { AuthTier.fromWireValue(device.authTier) }

    Card(
        modifier = modifier.fillMaxWidth().testTag("device_row_${device.kid}"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = device.kid,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                DeviceStatusBadge(status = device.status)
            }

            if (device.alias.isNotBlank()) {
                Text(
                    text = "Alias: ${device.alias}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Tier: ${device.authTier.ifBlank { "not recorded" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (device.enrolledAtEpochSeconds > 0L) {
                Text(
                    text = "Enrolled at: ${device.enrolledAtEpochSeconds}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (tier == null && device.authTier.isNotBlank()) {
                Text(
                    text = "The gateway recorded a confirmation tier this console does not " +
                        "recognise. It is shown as recorded, not interpreted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isLocalDevice) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("device_local_badge"),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "This device. Revoking it stops this console from signing.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            HorizontalDivider()

            OutlinedButton(
                onClick = onRevoke,
                modifier = Modifier.testTag("device_revoke_${device.kid}"),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Revoke ${device.kid}")
            }
        }
    }
}

@Composable
private fun DeviceStatusBadge(
    status: String,
    modifier: Modifier = Modifier,
) {
    val normalized = status.trim().lowercase()
    val container = when (normalized) {
        "active" -> MaterialTheme.colorScheme.primaryContainer
        "retired", "revoked" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        modifier = modifier.testTag("device_status_badge"),
        shape = RoundedCornerShape(6.dp),
        color = container,
    ) {
        Text(
            text = status.ifBlank { "unknown status" },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * The revocation confirmation.
 *
 * The confirm button stays disabled until the typed text is exactly the gateway's
 * confirmation string for that key. The reason is optional — the gateway accepts
 * `reason?` — but it is asked for, because a revocation without a reason leaves the
 * audit trail without the context that explains it.
 */
@Composable
private fun RevokeConfirmationDialog(
    kid: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var typedConfirmation by remember { mutableStateOf("") }

    val expected = remember(kid) { revokeConfirmationFor(kid) }
    val matches = typedConfirmation.trim() == expected

    AlertDialog(
        modifier = modifier.testTag("device_revoke_dialog"),
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
            )
        },
        title = { Text("Revoke $kid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Revoking retires this key permanently. The device can no longer " +
                        "sign, and the key cannot be brought back.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (recorded with the revocation)") },
                    modifier = Modifier.fillMaxWidth().testTag("device_revoke_reason_field"),
                )

                OutlinedTextField(
                    value = typedConfirmation,
                    onValueChange = { typedConfirmation = it },
                    label = { Text("Type $expected to confirm") },
                    singleLine = true,
                    isError = typedConfirmation.isNotEmpty() && !matches,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_revoke_confirmation_field"),
                )

                Text(
                    text = if (matches) {
                        "The confirmation matches. Revoking will be recorded in the audit trail."
                    } else {
                        "The confirmation does not match yet. The gateway refuses a " +
                            "revocation whose confirmation is not exactly \"$expected\"."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = matches,
                modifier = Modifier.testTag("device_revoke_confirm_button"),
            ) {
                Text("Revoke")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("device_revoke_cancel_button"),
            ) {
                Text("Cancel")
            }
        },
    )
}

/**
 * The outcome of the last revocation attempt.
 */
@Composable
private fun RevokeOutcomeCard(
    outcome: RevokeOutcome,
    modifier: Modifier = Modifier,
) {
    val container = if (outcome.revoked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("device_revoke_outcome_card"),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (outcome.revoked) Icons.Default.Info else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = if (outcome.revoked) {
                        "Revoked ${outcome.kid}"
                    } else {
                        "${outcome.kid} was not revoked"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            outcome.revokedAtEpochSeconds?.let { revokedAt ->
                Text(
                    text = "Revoked at: $revokedAt",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = "Audit: ${outcome.auditRef ?: "no audit reference returned"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("device_revoke_audit_ref"),
            )

            if (outcome.revoked && outcome.auditRef.isNullOrBlank()) {
                Text(
                    text = "The gateway accepted the revocation without returning an audit " +
                        "reference. Confirm it in the audit journal before relying on it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
