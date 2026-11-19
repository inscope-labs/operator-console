package com.inscopelabs.buildengine.ui.operations

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.core.security.AuthTier
import com.inscopelabs.buildengine.domain.model.SecurityPosture
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.CapabilitySummaryCard
import com.inscopelabs.buildengine.ui.components.ConfirmationScaffold
import com.inscopelabs.buildengine.ui.components.ErrorBanner

/**
 * The engine branch that halt and resume are scoped to.
 *
 * docs/schemas/halt.schema.json and docs/schemas/resume.schema.json both constrain
 * `branch` to the single value `engine`. Halt and resume are repository-wide: they stop
 * and restart every plan, so they cannot be bound to a task branch (I-07).
 */
const val ENGINE_BRANCH: String = "engine"

/**
 * The engine's state as the console can see it.
 *
 * `haltReason` is the recorded cause of the halt. The gateway's audit journal
 * (docs/gateway-contract.md §3.20) records the operation but carries no reason field, so
 * the cause the console shows is the one it recorded itself in its own operation journal
 * when it halted the engine (`OperationIntent.reason`). A blank [haltReason] means the
 * console does not know why the engine is stopped.
 */
data class EngineState(
    val isHalted: Boolean,
    val haltReason: String = "",
    val haltedAtEpochSeconds: Long? = null,
    /** The engine head the capability would be bound to. */
    val engineHeadSha: String = "",
    val branch: String = ENGINE_BRANCH,
) {
    /** Whether the console knows why the engine is stopped. */
    val hasRecordedCause: Boolean
        get() = haltReason.isNotBlank()
}

/**
 * The outcome of the last halt or resume attempt.
 */
data class EngineOperationOutcome(
    val operation: String,
    val accepted: Boolean,
    val auditRef: String = "",
    val headSha: String = "",
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/**
 * Everything the halt and resume screen shows.
 */
data class HaltResumeData(
    val engine: EngineState,
    val lastOutcome: EngineOperationOutcome? = null,
    /** The confirmation tier the engine operations require (BD-3.1). */
    val authTier: AuthTier? = null,
    val posture: SecurityPosture? = null,
    /** The capability TTL the operator is shown before confirming, in seconds. */
    val capabilityTtlSeconds: Long = 300L,
)

/**
 * Operator halt and resume (Stage 4.4 / T4-401).
 *
 * ## What this screen authorises
 *
 * Halt and resume are engine-scoped writes (contract §3.11–§3.12,
 * `docs/schemas/halt.schema.json`, `docs/schemas/resume.schema.json`). The scope is the
 * `engine` branch, never a task branch — the console never offers to halt a single plan
 * or reuse a task-scoped capability to do it (I-07).
 *
 * ## Why the resume is gated on the recorded cause
 *
 * Resume requires the cause recorded in the local operation journal when the engine was
 * halted (`OperationIntent.reason`). When the cause is unknown the screen says so and refuses
 * to resume (PR3-024).
 *
 * ## Boundaries this screen keeps
 *
 * - It authorises nothing by itself. It passes the operator's confirmation to
 *   [HaltResumeViewModel], which delegates exclusively to `HaltUseCase` / `ResumeUseCase`.
 * - It never shows a task-scoped scope for a halt or a resume.
 * - A halt or resume refused by the gateway is shown as refused.
 */
@Composable
fun HaltResumeScreen(
    modifier: Modifier = Modifier,
    viewModel: HaltResumeViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        if (viewModel.uiState.value is UiState.Idle) {
            viewModel.load()
        }
    }
    HaltResumeScreen(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onRefresh = { viewModel.load() },
        onHalt = { reason, typedHeadConfirmation ->
            viewModel.halt(reason = reason, typedHeadConfirmation = typedHeadConfirmation)
        },
        onResume = { _, typedHeadConfirmation ->
            viewModel.resume(typedHeadConfirmation = typedHeadConfirmation)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaltResumeScreen(
    state: UiState<HaltResumeData>,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onHalt: (reason: String, typedHeadConfirmation: String?) -> Unit = { _, _ -> },
    onResume: (reason: String, typedHeadConfirmation: String?) -> Unit = { _, _ -> },
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("halt_resume_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Halt and resume") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("halt_resume_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (state) {
            is UiState.Idle -> HaltIdleContent(
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
            )

            is UiState.Loading -> Column(
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).testTag("halt_resume_loading"),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Reading the engine state…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UiState.Error -> ErrorBanner(
                message = state.message,
                onRetry = onRefresh,
                modifier = Modifier.padding(innerPadding).testTag("halt_resume_error"),
            )

            is UiState.Success -> HaltResumeContent(
                data = state.data,
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
                onHalt = onHalt,
                onResume = onResume,
            )
        }
    }
}

@Composable
private fun HaltIdleContent(
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
            modifier = Modifier.testTag("halt_resume_idle"),
        )
        Text(
            text = "The engine state is read on demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("halt_resume_load_button"),
        ) {
            Text("Load engine state")
        }
    }
}

@Composable
private fun HaltResumeContent(
    data: HaltResumeData,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onHalt: (reason: String, typedHeadConfirmation: String?) -> Unit,
    onResume: (reason: String, typedHeadConfirmation: String?) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("halt_resume_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EngineStateCard(
                engine = data.engine,
                onRefresh = onRefresh,
            )
        }

        if (data.engine.isHalted) {
            item {
                HaltActiveCard(engine = data.engine)
            }
        }

        data.lastOutcome?.let { outcome ->
            item {
                EngineOutcomeCard(outcome = outcome)
            }
        }

        item {
            if (data.engine.isHalted) {
                ResumeActionCard(
                    data = data,
                    onResume = onResume,
                )
            } else {
                HaltActionCard(
                    data = data,
                    onHalt = onHalt,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * The engine's state and the scope a halt or resume would bind to.
 */
@Composable
private fun EngineStateCard(
    engine: EngineState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("engine_state_card"),
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
                Text(
                    text = "Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("engine_refresh_button"),
                ) {
                    Text("Refresh")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (engine.isHalted) {
                        Icons.Default.Warning
                    } else {
                        Icons.Default.Info
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (engine.isHalted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = if (engine.isHalted) "Halted" else "Running",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (engine.isHalted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.testTag("engine_state_label"),
                )
            }

            Text(
                text = "Scope: ${engine.branch} (repository-wide)",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Engine head: ${engine.engineHeadSha.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "A halt stops every plan. It is not scoped to a task, and a " +
                    "task-scoped capability cannot be used for it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The banner that says the engine is stopped, and why.
 */
@Composable
private fun HaltActiveCard(
    engine: EngineState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("halt_active_banner"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "The engine is halted",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Writes are refused while the engine is stopped. A resume is needed " +
                    "before any publish, upload or reconcile can proceed.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Recorded cause: ${engine.haltReason.ifBlank { "not recorded" }}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("halt_cause_text"),
            )

            engine.haltedAtEpochSeconds?.let { haltedAt ->
                Text(
                    text = "Halted at: $haltedAt",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * The outcome of the last halt or resume.
 */
@Composable
private fun EngineOutcomeCard(
    outcome: EngineOperationOutcome,
    modifier: Modifier = Modifier,
) {
    val container = if (outcome.accepted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = modifier.fillMaxWidth().testTag("engine_outcome_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (outcome.accepted) {
                    "${outcome.operation} accepted"
                } else {
                    "${outcome.operation} refused"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("engine_outcome_label"),
            )

            if (outcome.auditRef.isNotBlank()) {
                Text(
                    text = "Audit: ${outcome.auditRef}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (outcome.headSha.isNotBlank()) {
                Text(
                    text = "Head: ${outcome.headSha}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            outcome.errorCode?.let { code ->
                Text(
                    text = "Refused: $code",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("engine_outcome_error_code"),
                )
            }

            outcome.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The halt action.
 *
 * The contract requires a `reason` on the halt request (docs/schemas/halt.schema.json),
 * so the reason is a required field rather than a note: it is what the audit trail says
 * about why the engine was stopped. The operator confirms explicitly before anything is
 * minted or sent.
 */
@Composable
private fun HaltActionCard(
    data: HaltResumeData,
    modifier: Modifier = Modifier,
    onHalt: (reason: String, typedHeadConfirmation: String?) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var typedConfirmation by remember { mutableStateOf("") }

    val engine = data.engine
    val tier = data.authTier
    val expectedPrefix = remember(engine.engineHeadSha) { engine.engineHeadSha.take(7) }

    val typedConfirmationSatisfied = when (tier) {
        AuthTier.IN_APP_CONFIRM -> expectedPrefix.isNotEmpty() &&
            expectedPrefix.equals(typedConfirmation.trim(), ignoreCase = true)
        else -> true
    }

    val reasonSatisfied = reason.trim().isNotEmpty()
    val canConfirm = reasonSatisfied && typedConfirmationSatisfied

    Card(
        modifier = modifier.fillMaxWidth().testTag("halt_action_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            ConfirmationScaffold(
                title = "Halt the engine",
                body = "This stops every plan in the repository until somebody resumes it. " +
                    "It is recorded in the audit trail with the reason below.",
                confirmLabel = "Halt engine",
                onConfirm = {
                    val typed = typedConfirmation.trim()
                        .takeIf { tier == AuthTier.IN_APP_CONFIRM && it.isNotEmpty() }
                    onHalt(reason.trim(), typed)
                },
                onDismiss = {
                    reason = ""
                    typedConfirmation = ""
                },
                isConfirmEnabled = canConfirm,
                modifier = Modifier.testTag("halt_confirmation"),
            ) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Why is the engine being stopped?") },
                    supportingText = {
                        Text("Recorded with the halt so the audit trail says why.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("halt_reason_field"),
                )

                CapabilitySummaryCard(
                    operation = "HALT",
                    branch = engine.branch,
                    headSha = engine.engineHeadSha,
                    ttlSeconds = data.capabilityTtlSeconds,
                    authTier = tier,
                    posture = data.posture,
                    typedConfirmation = typedConfirmation,
                    onTypedConfirmationChange = { typedConfirmation = it },
                )
            }

            if (!reasonSatisfied) {
                Text(
                    text = "A reason is required before the engine can be halted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

/**
 * The resume action.
 *
 * Resume is offered only when the engine is halted, and only when the cause of the halt
 * is known — the console will not clear a halt it cannot explain.
 */
@Composable
private fun ResumeActionCard(
    data: HaltResumeData,
    modifier: Modifier = Modifier,
    onResume: (reason: String, typedHeadConfirmation: String?) -> Unit,
) {
    var typedConfirmation by remember { mutableStateOf("") }

    val engine = data.engine
    val tier = data.authTier
    val expectedPrefix = remember(engine.engineHeadSha) { engine.engineHeadSha.take(7) }

    val typedConfirmationSatisfied = when (tier) {
        AuthTier.IN_APP_CONFIRM -> expectedPrefix.isNotEmpty() &&
            expectedPrefix.equals(typedConfirmation.trim(), ignoreCase = true)
        else -> true
    }

    val causeKnown = engine.hasRecordedCause
    val canConfirm = causeKnown && typedConfirmationSatisfied

    Card(
        modifier = modifier.fillMaxWidth().testTag("resume_action_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            if (!causeKnown) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("resume_missing_cause"),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "The cause of the halt is not recorded",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "The console will not resume an engine it cannot explain. " +
                                "The reason the engine was halted has to be recovered from the " +
                                "operation journal before a resume can be offered.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            ConfirmationScaffold(
                title = "Resume the engine",
                body = if (causeKnown) {
                    "The engine starts handing out work again. The recorded cause of the " +
                        "halt is sent with the resume."
                } else {
                    "The recorded cause of the halt is required before the engine can be " +
                        "resumed."
                },
                confirmLabel = "Resume engine",
                onConfirm = {
                    val typed = typedConfirmation.trim()
                        .takeIf { tier == AuthTier.IN_APP_CONFIRM && it.isNotEmpty() }
                    onResume(engine.haltReason.trim(), typed)
                },
                onDismiss = { typedConfirmation = "" },
                isConfirmEnabled = canConfirm,
                modifier = Modifier.testTag("resume_confirmation"),
            ) {
                if (causeKnown) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("resume_cause_field"),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Recorded cause of the halt",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = engine.haltReason,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                CapabilitySummaryCard(
                    operation = "RESUME",
                    branch = engine.branch,
                    headSha = engine.engineHeadSha,
                    ttlSeconds = data.capabilityTtlSeconds,
                    authTier = tier,
                    posture = data.posture,
                    typedConfirmation = typedConfirmation,
                    onTypedConfirmationChange = { typedConfirmation = it },
                )
            }
        }
    }
}
