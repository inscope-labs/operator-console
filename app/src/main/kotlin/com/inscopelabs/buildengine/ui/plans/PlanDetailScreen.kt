package com.inscopelabs.buildengine.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
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
import com.inscopelabs.buildengine.domain.model.PlanStatus
import com.inscopelabs.buildengine.domain.model.PlanValidation
import com.inscopelabs.buildengine.domain.model.RenderCheck
import com.inscopelabs.buildengine.domain.model.Task
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.ErrorBanner

/**
 * Screen displaying details, validation status, and render checks for a single plan of record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    planId: String,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    LaunchedEffect(planId) {
        viewModel.loadPlanDetail(planId)
    }

    val state by viewModel.planDetailState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("plan_detail_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("plan_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadPlanDetail(planId) },
                        modifier = Modifier.testTag("plan_detail_refresh_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val currentState = state) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.testTag("plan_detail_loading"),
                        )
                    }
                }
                is UiState.Error -> {
                    ErrorBanner(
                        message = currentState.message,
                        onRetry = { viewModel.loadPlanDetail(planId) },
                    )
                }
                is UiState.Success -> {
                    PlanDetailContent(
                        data = currentState.data,
                        planId = planId,
                        onValidate = { viewModel.validatePlan(planId) },
                        onCheckRender = { viewModel.checkRender(planId) },
                        onStartTask = { task, typedHead -> viewModel.startTask(task, typedHead) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanDetailContent(
    data: PlanDetailData,
    planId: String,
    onValidate: () -> Unit,
    onCheckRender: () -> Unit,
    onStartTask: (Task, String?) -> Unit,
) {
    val plan = data.plan
    val status = data.status

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Plan Overview Card
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plan_overview_card"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (plan.status.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = plan.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ID: ${plan.id}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (plan.taskCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = plan.completedTaskCount.toFloat() / plan.taskCount.toFloat()
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${plan.completedTaskCount} of ${plan.taskCount} tasks completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Status Card
        if (status != null) {
            item {
                StatusCard(status = status)
            }
        }

        // Work order: the documented alternative to a work-order creation action.
        // Work-order creation is deferred to engine tooling (BD-1.3 Option B, PR3-027).
        item {
            WorkOrderDeferralCard()
        }

        // Single operator entry point for SET_IN_PROGRESS (PENDING -> IN_PROGRESS, ADR 0016 D-1).
        item {
            StartableTasksCard(
                tasks = data.pendingTasks,
                authTier = data.authTier,
                startingTaskId = data.startingTaskId,
                feedback = data.transitionFeedback,
                onStartTask = onStartTask,
            )
        }

        // Actions: Validate & Render Check
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onValidate,
                            enabled = !data.isValidating,
                            modifier = Modifier.weight(1f).testTag("validate_plan_button"),
                        ) {
                            if (data.isValidating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.action_validate))
                        }

                        OutlinedButton(
                            onClick = onCheckRender,
                            enabled = !data.isRenderChecking,
                            modifier = Modifier.weight(1f).testTag("check_render_button"),
                        ) {
                            if (data.isRenderChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.action_render_check))
                        }
                    }
                }
            }
        }

        // Validation Results
        if (data.validation != null) {
            item {
                ValidationResultCard(validation = data.validation)
            }
        }

        // Render Check Results
        if (data.renderCheck != null) {
            item {
                RenderCheckResultCard(renderCheck = data.renderCheck)
            }
        }
    }
}

/**
 * The documented alternative to a work-order creation action (BD-1.3 Option B, PR3-027).
 *
 * Work orders are created by engine tooling. The gateway contract defines no work-order
 * creation request, response or schema, so the console has no such action to offer and must
 * not pretend otherwise (I-10). This card states where work orders come from instead of
 * presenting a control that cannot work.
 *
 * It is informational only: there is no button, and nothing here can reach the gateway.
 */
@Composable
private fun WorkOrderDeferralCard() {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("work_order_deferral_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.work_order_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.work_order_deferral_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StartableTasksCard(
    tasks: List<Task>,
    authTier: AuthTier?,
    startingTaskId: String?,
    feedback: TaskTransitionFeedback?,
    onStartTask: (Task, String?) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("startable_tasks_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_start_tasks_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.plan_start_tasks_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (feedback != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_task_feedback"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (feedback.accepted) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${feedback.taskTitle} (${feedback.taskId})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = feedback.message,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        feedback.auditRef?.takeIf { it.isNotBlank() }?.let { audit ->
                            Text(
                                text = stringResource(R.string.publish_audit_ref, audit),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.plan_no_pending_tasks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("plan_no_pending_tasks_text"),
                )
            } else {
                tasks.forEach { task ->
                    var typedConfirmation by remember(task.id, task.headSha) { mutableStateOf("") }
                    val expectedPrefix = task.headSha.take(7)
                    val needsTypedHead = authTier == AuthTier.IN_APP_CONFIRM
                    val typedMatches = !needsTypedHead ||
                        (expectedPrefix.isNotEmpty() && expectedPrefix.equals(typedConfirmation.trim(), ignoreCase = true))
                    val canStart = startingTaskId == null &&
                        task.order.isNotBlank() &&
                        task.branch.isNotBlank() &&
                        task.headSha.isNotBlank() &&
                        typedMatches

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pending_task_item_${task.id}"),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Task ${task.id} · Order: ${task.order.ifBlank { "missing" }}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Branch: ${task.branch.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Head: ${task.headSha.ifBlank { "—" }}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            if (task.order.isBlank()) {
                                Text(
                                    text = stringResource(R.string.plan_start_task_order_missing),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            if (needsTypedHead) {
                                OutlinedTextField(
                                    value = typedConfirmation,
                                    onValueChange = { typedConfirmation = it },
                                    label = {
                                        Text(stringResource(R.string.plan_start_task_typed_head, 7))
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("start_task_typed_head_${task.id}"),
                                )
                            }

                            Button(
                                onClick = {
                                    onStartTask(task, typedConfirmation.trim().takeIf { it.isNotEmpty() })
                                },
                                enabled = canStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("start_task_button_${task.id}"),
                            ) {
                                if (startingTaskId == task.id) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(stringResource(R.string.action_start_task))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: PlanStatus) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan_status_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Plan Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Milestone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.label_milestone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = status.currentMilestone.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Engine state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status.halted) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "HALTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = status.status.ifEmpty { "ACTIVE" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Open branches
            if (status.openBranches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.open_branches_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    status.openBranches.forEach { branch ->
                        Text(
                            text = branch,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Blocked tasks
            if (status.blockedTasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.label_blocked_tasks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    status.blockedTasks.forEach { task ->
                        Text(
                            text = task,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationResultCard(validation: PlanValidation) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("validation_result_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (validation.valid) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (validation.valid) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (validation.valid) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = if (validation.valid) {
                        stringResource(R.string.label_validation_passed)
                    } else {
                        stringResource(R.string.label_validation_failed)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (validation.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Errors:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                validation.errors.forEach { err ->
                    Text(
                        text = "• $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }

            if (validation.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Warnings:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                validation.warnings.forEach { warn ->
                    Text(
                        text = "• $warn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderCheckResultCard(renderCheck: RenderCheck) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("render_check_result_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (renderCheck.ok) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (renderCheck.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (renderCheck.ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = if (renderCheck.ok) {
                        stringResource(R.string.label_render_current)
                    } else {
                        stringResource(R.string.label_render_stale)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (renderCheck.renderIssues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Issues:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                renderCheck.renderIssues.forEach { issue ->
                    Text(
                        text = "• $issue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}
