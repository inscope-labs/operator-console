package com.inscopelabs.buildengine.ui.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.lab.ContractCheck
import com.inscopelabs.buildengine.lab.LabReport
import com.inscopelabs.buildengine.lab.Scenario
import com.inscopelabs.buildengine.lab.ScenarioCatalogue
import com.inscopelabs.buildengine.lab.ScenarioResult
import com.inscopelabs.buildengine.lab.ScenarioRunner
import com.inscopelabs.buildengine.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the Lab screen shows.
 *
 * `labEnabled` is the Lab toggle. It starts **off** and it is never persisted: it
 * lives in this state object and nowhere else, so the Lab is off again after a
 * process death (T5-304, ADR 0004 §4).
 */
data class LabUiState(
    /** Whether Lab Mode is switched on. Off by default, and never persisted. */
    val labEnabled: Boolean = false,
    val isRunning: Boolean = false,
    /** The catalogue the screen lists. */
    val scenarios: List<Scenario> = emptyList(),
    /** The outcome of each scenario that has run, keyed by scenario id. */
    val results: Map<String, ScenarioResult> = emptyMap(),
    /** The report of the last run, once there is one. */
    val report: LabReport? = null,
    /** Why the Lab refused to run, when it refused. */
    val guardMessage: String = "",
    /** How many scenarios have run so far. */
    val completed: Int = 0,
    /** How many scenarios the catalogue holds. */
    val total: Int = 0,
) {
    /** Whether the Lab may run anything at all. */
    val canRun: Boolean
        get() = labEnabled && !isRunning
}

/**
 * Lab screen state and scenario orchestration.
 *
 * **Proposed baseline.** This is a pre-execution proposal produced against
 * `docs/PRE-EXECUTION-ONBOARDING.md`, not a completed stage. It has not been compiled,
 * has no test coverage, and is not wired into navigation, DI or any caller. The AI
 * Studio agent executes Stage 5.3, corrects this file against the real use cases,
 * adds the tests, and closes the stage only when CI is green on the pushed SHA (G-04).
 *
 * TODO(Stage 5.3 / T5-301): supply the lab signer (T5-302), wire the environment
 * guard before each scenario (T5-303), keep the Lab toggle off by default and
 * unpersisted (T5-304), and add the state tests the gate requires.
 *
 * ### What this class holds
 *
 * The scenario catalogue, the outcome of each scenario, and the report of the last
 * run. It holds **no capability, no signature and no nonce** — the runner keeps those
 * in memory for the length of a request and hands back only what the gateway said.
 *
 * ### The Lab toggle
 *
 * T5-304: off by default, never persisted. The toggle is a field of [LabUiState] and
 * nothing writes it to a store, so it is off again the moment the process ends. There
 * is deliberately no preference key, no datastore entry and no `savedStateHandle`
 * write for it.
 *
 * ### The catalogue in `init`
 *
 * The catalogue is a static object, not a network read, so it is loaded with the
 * ViewModel rather than fetched. The rule that a ViewModel makes no network call in
 * `init` (T2-101) is not touched by this: nothing here talks to a gateway.
 *
 * ### The signature below is a proposal
 *
 * The stage owns the final shape of this class.
 */
@HiltViewModel
class LabViewModel @Inject constructor(
    private val scenarioRunner: ScenarioRunner,
) : ViewModel() {

    /** The scenario catalogue. A static object — nothing is fetched to build it. */
    private val catalogue: List<Scenario> = ScenarioCatalogue.all()

    private val _uiState = MutableStateFlow<UiState<LabUiState>>(
        UiState.Success(
            LabUiState(
                scenarios = catalogue,
                total = catalogue.size,
            ),
        ),
    )
    val uiState: StateFlow<UiState<LabUiState>> = _uiState.asStateFlow()

    /** The run in flight, so it can be cancelled. */
    private var runJob: Job? = null

    /**
     * Switches Lab Mode on or off.
     *
     * Switching it off stops nothing that is already running — a run in flight is
     * cancelled by [onCancel] — but it does refuse the next one.
     */
    fun onLabEnabledChange(enabled: Boolean) {
        val current = state()
        _uiState.value = UiState.Success(
            current.copy(
                labEnabled = enabled,
                guardMessage = if (enabled) "" else LAB_OFF_MESSAGE,
            ),
        )
    }

    /**
     * Runs the whole catalogue.
     */
    fun onRunAll() {
        val current = state()
        if (!current.canRun) return
        startRun(current.scenarios)
    }

    /**
     * Runs one scenario.
     */
    fun onRunScenario(scenarioId: String) {
        val current = state()
        if (!current.canRun) return
        val scenario = current.scenarios.firstOrNull { it.id == scenarioId } ?: return
        startRun(listOf(scenario))
    }

    /**
     * Cancels the run in flight.
     *
     * The runner checks for cancellation between scenarios, so a cancelled run stops
     * after the scenario it is in. The scenarios it already ran keep their results.
     */
    fun onCancel() {
        runJob?.cancel()
        runJob = null
        val current = state()
        _uiState.value = UiState.Success(
            current.copy(isRunning = false, guardMessage = "The run was cancelled."),
        )
    }

    /**
     * Throws the report away and starts over.
     *
     * The report is held in memory only (ADR 0004 §4), so this is how it is discarded.
     */
    fun onClearReport() {
        val current = state()
        _uiState.value = UiState.Success(
            current.copy(report = null, results = emptyMap(), completed = 0, guardMessage = ""),
        )
    }

    /**
     * The report as JSON, for the explicit share action.
     *
     * Returns an empty string when there is no report, so the screen can refuse to
     * share rather than share an empty document.
     */
    fun reportJson(): String = state().report?.toJson().orEmpty()

    /**
     * The contract drift the last run's observations showed.
     */
    fun lastDrifts(): List<String> = state().report?.drifts?.map { it.summary }.orEmpty()

    /**
     * Runs [scenarios] and collects the results.
     */
    private fun startRun(scenarios: List<Scenario>) {
        val current = state()
        _uiState.value = UiState.Success(
            current.copy(isRunning = true, guardMessage = "", completed = 0),
        )

        runJob = viewModelScope.launch {
            val run = scenarioRunner.run(scenarios)
            val drifts = ContractCheck.run(run.observations)
            val report = LabReport(
                runId = "lab-${run.startedAtEpochSeconds}",
                environment = run.environment,
                startedAtEpochSeconds = run.startedAtEpochSeconds,
                finishedAtEpochSeconds = run.finishedAtEpochSeconds,
                results = run.results,
                drifts = drifts,
                catalogueSize = current.total,
            )
            val latest = state()
            _uiState.value = UiState.Success(
                latest.copy(
                    isRunning = false,
                    results = run.results.associateBy { it.scenarioId },
                    report = report,
                    completed = run.results.size,
                    guardMessage = if (run.environment.isBlank()) {
                        "The Lab did not run: the gateway is not a test environment."
                    } else {
                        ""
                    },
                ),
            )
        }
    }

    /**
     * The current state.
     */
    private fun state(): LabUiState = (_uiState.value as? UiState.Success)?.data
        ?: LabUiState(scenarios = catalogue, total = catalogue.size)

    private companion object {
        const val LAB_OFF_MESSAGE: String =
            "Lab Mode is off. Switch it on to run scenarios against a test gateway."
    }
}
