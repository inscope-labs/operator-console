package com.inscopelabs.buildengine.manualpipeline.ui.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ReconcileOperationUseCase
import com.inscopelabs.buildengine.manualpipeline.domain.usecase.ResolveAuthorityUseCase
import com.inscopelabs.buildengine.manualpipeline.ui.act.ActStateMachine
import com.inscopelabs.buildengine.manualpipeline.ui.act.handoff.HandoffVerificationView
import com.inscopelabs.buildengine.manualpipeline.ui.act.recovery.RecoveryScreen
import com.inscopelabs.buildengine.manualpipeline.ui.chrome.ManualPipelineTopBar
import com.inscopelabs.buildengine.manualpipeline.ui.chrome.ReadOnlyBanner
import com.inscopelabs.buildengine.manualpipeline.ui.state.CanvasMode
import com.inscopelabs.buildengine.manualpipeline.ui.state.InspectTab
import com.inscopelabs.buildengine.manualpipeline.ui.state.ManualPipelineSession
import com.inscopelabs.buildengine.manualpipeline.ui.state.ReadOnlyState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CanvasHostViewModel @Inject constructor(
    private val session: ManualPipelineSession,
    private val actStateMachine: ActStateMachine,
    private val resolveAuthorityUseCase: ResolveAuthorityUseCase,
    private val reconcileOperationUseCase: ReconcileOperationUseCase,
) : ViewModel() {

    val sessionState = session.state
    val actPhase = actStateMachine.phase

    private val _readOnlyState = MutableStateFlow(ReadOnlyState())
    val readOnlyState: StateFlow<ReadOnlyState> = _readOnlyState.asStateFlow()

    private val _pendingRecoveryCount = MutableStateFlow(0)
    val pendingRecoveryCount: StateFlow<Int> = _pendingRecoveryCount.asStateFlow()

    init {
        refreshChrome()
    }

    fun refreshChrome() {
        viewModelScope.launch {
            val report = resolveAuthorityUseCase()
            val task = session.state.value.selectedTask
            val envMismatch = !report.environmentValidation.matched
            val halted = task?.isPlanHalted == true
            val notEnrolled = !report.snapshot.enrolled
            val missingCred = !report.snapshot.hasOperatorCredential
            _readOnlyState.value = ReadOnlyState(
                isReadOnly = envMismatch || halted || notEnrolled || missingCred || !report.canAct,
                expectedEnvironment = report.snapshot.expectedEnvironment,
                reportedEnvironment = report.snapshot.reportedEnvironment,
                environmentMismatch = envMismatch,
                isHalted = halted,
                notEnrolled = notEnrolled,
                missingCredential = missingCred,
                reason = report.blockingReason ?: if (halted) "Selected task plan is halted." else null,
            )
            _pendingRecoveryCount.value = reconcileOperationUseCase.loadRecoveryQueue().size
        }
    }

    fun setCanvasMode(mode: CanvasMode) = session.setCanvasMode(mode)
    fun setInspectTab(tab: InspectTab) = session.setInspectTab(tab)
    fun openRecovery(open: Boolean) = session.openRecovery(open)
    fun openHandoff(open: Boolean) = session.openHandoff(open)
}

@Composable
fun CanvasHost(
    initialTaskId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CanvasHostViewModel = hiltViewModel(),
) {
    val session by viewModel.sessionState.collectAsState()
    val actPhase by viewModel.actPhase.collectAsState()
    val readOnly by viewModel.readOnlyState.collectAsState()
    val pendingRecoveryCount by viewModel.pendingRecoveryCount.collectAsState()

    LaunchedEffect(session.selectedTask?.id, session.selectedTask?.isPlanHalted) {
        viewModel.refreshChrome()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("mp_canvas_host"),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ManualPipelineTopBar(
                title = "Manual Pipeline",
                subtitle = session.selectedTask?.let { "${it.id} • ${it.branch}" } ?: "No task selected",
                expectedEnvironment = readOnly.expectedEnvironment,
                reportedEnvironment = readOnly.reportedEnvironment,
                pendingRecoveryCount = pendingRecoveryCount,
                onBack = {
                    when {
                        session.canvas.recoveryOpen -> viewModel.openRecovery(false)
                        session.canvas.handoffOpen -> viewModel.openHandoff(false)
                        else -> onBack()
                    }
                },
                onRefresh = viewModel::refreshChrome,
                onOpenRecovery = { viewModel.openRecovery(true) },
                onOpenHandoff = { viewModel.openHandoff(true) },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val useRail = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail && !session.canvas.recoveryOpen && !session.canvas.handoffOpen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        NavigationRailItem(
                            selected = session.canvas.mode == CanvasMode.INSPECT,
                            onClick = { viewModel.setCanvasMode(CanvasMode.INSPECT) },
                            icon = { Icon(Icons.Default.Search, contentDescription = null) },
                            label = { Text("Inspect") },
                            modifier = Modifier.testTag("mp_rail_inspect"),
                        )
                        NavigationRailItem(
                            selected = session.canvas.mode == CanvasMode.ACT,
                            onClick = { viewModel.setCanvasMode(CanvasMode.ACT) },
                            icon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                            label = { Text("Act") },
                            modifier = Modifier.testTag("mp_rail_act"),
                        )
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        if (readOnly.isReadOnly) {
                            ReadOnlyBanner(
                                state = readOnly,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }

                        when {
                            session.canvas.recoveryOpen -> {
                                RecoveryScreen(modifier = Modifier.fillMaxSize())
                            }
                            session.canvas.handoffOpen -> {
                                HandoffVerificationView(modifier = Modifier.fillMaxSize())
                            }
                            else -> {
                                if (!useRail) {
                                    CanvasSwitcher(
                                        mode = session.canvas.mode,
                                        onModeSelected = viewModel::setCanvasMode,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                                when (session.canvas.mode) {
                                    CanvasMode.INSPECT -> InspectCanvas(
                                        activeTab = session.canvas.activeInspectTab,
                                        initialTaskId = initialTaskId,
                                        onSelectTab = viewModel::setInspectTab,
                                        modifier = Modifier.weight(1f),
                                    )
                                    CanvasMode.ACT -> ActCanvas(
                                        phase = actPhase,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
