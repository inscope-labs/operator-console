package com.inscopelabs.buildengine.lab

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * The counts a report carries in its header.
 */
data class LabReportSummary(
    val total: Int,
    val passed: Int,
    val unexpected: Int,
    val transportFailures: Int,
    val skipped: Int,
    val drifts: Int,
) {
    /** Whether the run was clean: everything passed and nothing drifted. */
    val isClean: Boolean
        get() = unexpected == 0 && transportFailures == 0 && drifts == 0
}

/**
 * The in-memory Lab report (T5-102).
 *
 * ADR 0004 §4: the report is held in memory only. There is no table, no file and no
 * preference for it, and this class writes none. The only way it leaves the device is
 * the explicit share action, which hands the JSON to the system share sheet — an
 * action the operator takes deliberately (ADR 0004 §4).
 *
 * **The report carries no signature, no nonce and no credential.** It records what the
 * gateway answered: the scenario, the code it was expected to return, the code it
 * returned, the status, and how long it took. [ScenarioResult] has no field that
 * could hold a capability, so there is nothing here to redact.
 */
data class LabReport(
    val runId: String,
    /** The environment the run was made against. Empty when the guard refused the run. */
    val environment: String,
    val startedAtEpochSeconds: Long,
    val finishedAtEpochSeconds: Long,
    val results: List<ScenarioResult>,
    /** The contract drift the run's observations showed. */
    val drifts: List<ContractDrift> = emptyList(),
    /** How many scenarios the catalogue holds, so a truncated run is visible. */
    val catalogueSize: Int = results.size,
) {

    /**
     * The report's counts.
     */
    fun summary(): LabReportSummary = LabReportSummary(
        total = results.size,
        passed = results.count { it.isPass },
        unexpected = results.count { it is ScenarioResult.Unexpected },
        transportFailures = results.count { it is ScenarioResult.TransportFailure },
        skipped = results.count { it is ScenarioResult.Skipped },
        drifts = drifts.size,
    )

    /**
     * The report as JSON.
     *
     * Plain JSON text, which is what the share sheet takes. The shape is the report's
     * own: there is no published schema for a Lab report, and inventing one would
     * imply a contract nobody has agreed to.
     */
    fun toJson(): String = buildJsonObject {
        put("runId", JsonPrimitive(runId))
        put("environment", JsonPrimitive(environment))
        put("startedAtEpochSeconds", JsonPrimitive(startedAtEpochSeconds))
        put("finishedAtEpochSeconds", JsonPrimitive(finishedAtEpochSeconds))
        put("catalogueSize", JsonPrimitive(catalogueSize))

        val counts = summary()
        put(
            "summary",
            buildJsonObject {
                put("total", JsonPrimitive(counts.total))
                put("passed", JsonPrimitive(counts.passed))
                put("unexpected", JsonPrimitive(counts.unexpected))
                put("transportFailures", JsonPrimitive(counts.transportFailures))
                put("skipped", JsonPrimitive(counts.skipped))
                put("drifts", JsonPrimitive(counts.drifts))
            },
        )

        put(
            "results",
            buildJsonArray {
                results.forEach { result -> add(result.toJson()) }
            },
        )

        put(
            "drifts",
            buildJsonArray {
                drifts.forEach { drift ->
                    add(
                        buildJsonObject {
                            put("kind", JsonPrimitive(drift.kind()))
                            put("summary", JsonPrimitive(drift.summary))
                        },
                    )
                }
            },
        )
    }.toString()

    /**
     * One result as a JSON object.
     */
    private fun ScenarioResult.toJson(): JsonObject = buildJsonObject {
        put("scenarioId", JsonPrimitive(scenarioId))
        put("expectedErrorCode", JsonPrimitive(expectedErrorCode))
        put("expectedStatus", JsonPrimitive(expectedStatus))
        put("observedErrorCode", JsonPrimitive(observedErrorCode))
        put("observedStatus", observedStatus?.let { JsonPrimitive(it) } ?: JsonNull)
        put("durationMillis", JsonPrimitive(durationMillis))
        put("outcome", JsonPrimitive(this@toJson.kind()))
        if (detail.isNotBlank()) {
            put("detail", JsonPrimitive(detail))
        }
    }

    /**
     * The name of a result's state.
     */
    private fun ScenarioResult.kind(): String = when (this) {
        is ScenarioResult.Passed -> "passed"
        is ScenarioResult.Unexpected -> "unexpected"
        is ScenarioResult.TransportFailure -> "transport_failure"
        is ScenarioResult.Skipped -> "skipped"
    }

    /**
     * The name of a drift's kind.
     */
    private fun ContractDrift.kind(): String = when (this) {
        is ContractDrift.UnknownErrorCode -> "unknown_error_code"
        is ContractDrift.StatusMismatch -> "status_mismatch"
        is ContractDrift.MissingField -> "missing_field"
        is ContractDrift.UnexpectedField -> "unexpected_field"
        is ContractDrift.ParentAliasInUse -> "parent_alias_in_use"
    }
}

/**
 * Builds the empty report the Lab shows before anything has been run.
 *
 * A run that never happened is not a run that found nothing: the report says so
 * rather than showing an empty result list that reads like a clean bill of health.
 */
fun emptyLabReport(catalogueSize: Int): LabReport = LabReport(
    runId = "",
    environment = "",
    startedAtEpochSeconds = 0L,
    finishedAtEpochSeconds = 0L,
    results = emptyList(),
    catalogueSize = catalogueSize,
)

/**
 * The JSON array a report's results are written into.
 *
 * Exposed so the screen can render the same JSON it would share, without inventing a
 * second rendering of the same data.
 */
fun LabReport.resultsAsJsonArray(): JsonArray = buildJsonArray {
    results.forEach { result ->
        add(
            buildJsonObject {
                put("scenarioId", JsonPrimitive(result.scenarioId))
                put("expectedErrorCode", JsonPrimitive(result.expectedErrorCode))
                put("observedErrorCode", JsonPrimitive(result.observedErrorCode))
                put("observedStatus", result.observedStatus?.let { JsonPrimitive(it) } ?: JsonNull)
            },
        )
    }
}
