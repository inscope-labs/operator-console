package com.inscopelabs.buildengine.lab

/**
 * Runs Lab scenarios sequentially; cancellable; no retries (T5-101).
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 * TODO(Stage 5.1 / T5-101): implement the runner. The signature below is a proposal; the stage may change it.
 *
 * Pre-execution scaffold (docs/PRE-EXECUTION-ONBOARDING.md). Not wired into navigation, DI or any caller.
 */
class ScenarioRunner {

    suspend fun run(scenarios: List<Scenario>): List<ScenarioResult> =
        TODO("SCAFFOLD: run each scenario in order, honour cancellation, never retry.")
}
