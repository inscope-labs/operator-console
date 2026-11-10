package com.inscopelabs.buildengine.ui.preflight

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.domain.model.preflight.CheckStatus
import com.inscopelabs.buildengine.domain.model.preflight.PreflightCheckId
import com.inscopelabs.buildengine.domain.model.preflight.PreflightCheckResult
import com.inscopelabs.buildengine.domain.model.preflight.PreflightReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreflightScreen(
    modifier: Modifier = Modifier,
    viewModel: PreflightViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEnrollment: () -> Unit = {},
) {
    val report by viewModel.report.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("preflight_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preflight_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("preflight_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.runChecks() },
                        enabled = !report.isRunning,
                        modifier = Modifier.testTag("preflight_refresh_button"),
                    ) {
                        if (report.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh),
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.runChecks() },
                modifier = Modifier.testTag("preflight_run_button"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                if (report.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.preflight_run_checks),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PreflightOverallHeader(report = report)
            }

            items(report.checks, key = { it.id.name }) { check ->
                PreflightCheckCard(
                    check = check,
                    onOpenSettings = onNavigateToSettings,
                    onOpenEnrollment = onNavigateToEnrollment,
                )
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun PreflightOverallHeader(
    report: PreflightReport,
    modifier: Modifier = Modifier,
) {
    val overallPass = report.overallPass
    val isRunning = report.isRunning

    val containerColor = when {
        isRunning -> MaterialTheme.colorScheme.surfaceVariant
        overallPass == true -> MaterialTheme.colorScheme.primaryContainer
        overallPass == false -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isRunning -> MaterialTheme.colorScheme.onSurfaceVariant
        overallPass == true -> MaterialTheme.colorScheme.onPrimaryContainer
        overallPass == false -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = when {
        isRunning -> Icons.Default.HourglassTop
        overallPass == true -> Icons.Default.CheckCircle
        overallPass == false -> Icons.Default.Error
        else -> Icons.Default.Warning
    }

    val headline = when {
        isRunning -> stringResource(R.string.preflight_running)
        overallPass == true -> stringResource(R.string.preflight_overall_pass)
        overallPass == false -> stringResource(R.string.preflight_overall_fail)
        else -> stringResource(R.string.preflight_pending)
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preflight_overall_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                    Text(
                        text = "Environment: ${report.expectedEnvironment} · Base: ${report.gatewayBaseUrl.ifEmpty { "unconfigured" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
            }

            if (report.checks.isNotEmpty()) {
                val passCount = report.checks.count { it.status == CheckStatus.PASS }
                val warnCount = report.checks.count { it.status == CheckStatus.WARN }
                val failCount = report.checks.count { it.status == CheckStatus.FAIL }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    CountPill(label = "$passCount passed", color = MaterialTheme.colorScheme.primary)
                    if (warnCount > 0) {
                        CountPill(label = "$warnCount warnings", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (failCount > 0) {
                        CountPill(label = "$failCount failed", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PreflightCheckCard(
    check: PreflightCheckResult,
    onOpenSettings: () -> Unit,
    onOpenEnrollment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(check.status == CheckStatus.FAIL) }

    val statusColor = when (check.status) {
        CheckStatus.PASS -> MaterialTheme.colorScheme.primary
        CheckStatus.WARN -> MaterialTheme.colorScheme.tertiary
        CheckStatus.FAIL -> MaterialTheme.colorScheme.error
        CheckStatus.RUNNING -> MaterialTheme.colorScheme.secondary
        CheckStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when (check.status) {
        CheckStatus.PASS -> Icons.Default.CheckCircle
        CheckStatus.WARN -> Icons.Default.Warning
        CheckStatus.FAIL -> Icons.Default.Error
        CheckStatus.RUNNING -> Icons.Default.HourglassTop
        CheckStatus.PENDING -> Icons.Default.HourglassTop
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preflight_check_${check.id.name.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = check.status.name,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = check.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                StatusBadge(status = check.status, color = statusColor)
            }

            Text(
                text = check.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (!check.detail.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (check.durationMs != null) {
                        Text(
                            text = "${check.durationMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse detail" else "Expand detail",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = check.detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Quick remediation actions for failed checks
            if (check.status == CheckStatus.FAIL || check.status == CheckStatus.WARN) {
                when (check.id) {
                    PreflightCheckId.CONFIGURED, PreflightCheckId.AUTHENTICATION -> {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(R.string.preflight_action_settings))
                        }
                    }
                    PreflightCheckId.ENROLMENT_STATE -> {
                        OutlinedButton(
                            onClick = onOpenEnrollment,
                            modifier = Modifier.padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(R.string.preflight_action_enrolment))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: CheckStatus,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
