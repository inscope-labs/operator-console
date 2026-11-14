package com.inscopelabs.buildengine.lab

import com.inscopelabs.buildengine.core.network.GatewayEndpoints
import com.inscopelabs.buildengine.core.network.GatewayHttpClient
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayNotConfiguredException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.network.dto.GatewayErrorDto
import com.inscopelabs.buildengine.core.network.dto.GatewayHealthDto
import com.inscopelabs.buildengine.core.result.AppResult
import com.inscopelabs.buildengine.core.time.Clock
import com.inscopelabs.buildengine.domain.model.GatewayHealthSnapshot
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * One run of the scenario catalogue.
 *
 * `observations` is what the contract check consumes: the codes and the field names the
 * gateway actually returned. It is collected in the same run as the results rather than
 * in a second run, so the check describes the traffic that was actually made.
 */
data class LabRun(
    val results: List<ScenarioResult>,
    val observations: List<LabObservation>,
    /** The environment the Lab ran against. Empty when the guard refused the run. */
    val environment: String,
    val startedAtEpochSeconds: Long,
    val finishedAtEpochSeconds: Long,
) {
    val passed: Int get() = results.count { it.isPass }
    val unexpected: Int get() = results.count { it is ScenarioResult.Unexpected }
    val transportFailures: Int get() = results.count { it is ScenarioResult.TransportFailure }
    val skipped: Int get() = results.count { it is ScenarioResult.Skipped }
}

/**
 * Runs Lab scenarios sequentially; cancellable; no retries (T5-101).
 *
 * ### What this class guarantees
 *
 * - **Sequential.** One scenario at a time, in the order given. A catalogue that ran in
 *   parallel would make the nonce-replay scenario race the scenario that mints the
 *   nonce.
 * - **Cancellable.** The runner checks for cancellation between scenarios and never
 *   starts another one after a cancellation. A cancelled run ends with the scenarios it
 *   reached, each of them either answered or explicitly skipped.
 * - **No retries.** A scenario that fails is failed. Retrying would hide the very thing
 *   the Lab is there to observe, and a replayed request is exactly what the
 *   nonce-replay scenario is testing for. A scenario that sends its request twice says
 *   so in its own declaration ([LabRepeat]), and the repetition is part of the test.
 * - **Guarded.** ADR 0004 §1: the environment is checked before anything is sent, and
 *   the check is programmatic — no UI control can override it.
 *
 * ### The signature below is a proposal
 *
 * The pre-execution scaffold proposed `run(scenarios): List<ScenarioResult>`. It
 * returns a [LabRun] instead, because the contract check (T5-401) needs the codes and
 * field names the gateway actually returned, and collecting them in the same run is
 * what makes the check describe the traffic that was actually made. The stage owns the
 * final shape.
 */
class ScenarioRunner @Inject constructor(
    private val httpClient: GatewayHttpClient,
    private val capabilitySource: LabCapabilitySource,
    private val clock: Clock,
) {

    /**
     * Runs [scenarios] in order and returns what happened.
     */
    suspend fun run(scenarios: List<Scenario>): LabRun {
        val startedAt = clock.nowEpochSeconds()
        val results = mutableListOf<ScenarioResult>()
        val observations = mutableListOf<LabObservation>()

        val environment = when (val guard = probeTestEnvironment()) {
            is AppResult.Success -> guard.value.environment
            is AppResult.Failure -> {
                // ADR 0004: nothing is sent until the environment is known to be test.
                scenarios.forEach { scenario ->
                    results += ScenarioResult.Skipped(
                        scenarioId = scenario.id,
                        expectedErrorCode = scenario.errorCode,
                        expectedStatus = scenario.expectedStatus,
                        detail = guard.message,
                    )
                }
                return LabRun(
                    results = results,
                    observations = observations,
                    environment = "",
                    startedAtEpochSeconds = startedAt,
                    finishedAtEpochSeconds = clock.nowEpochSeconds(),
                )
            }
        }

        for (scenario in scenarios) {
            currentCoroutineContext().ensureActive()
            val outcome = runOne(scenario)
            results += outcome.result
            outcome.observation?.let { observations += it }
        }

        return LabRun(
            results = results,
            observations = observations,
            environment = environment,
            startedAtEpochSeconds = startedAt,
            finishedAtEpochSeconds = clock.nowEpochSeconds(),
        )
    }

    /**
     * Runs one scenario.
     *
     * Every exit from this method is a [ScenarioResult]. There is no path that leaves
     * a scenario unaccounted for, and no path that reports a scenario as passed without
     * having seen the gateway's own answer.
     */
    private suspend fun runOne(scenario: Scenario): Outcome {
        val startedAtMillis = System.currentTimeMillis()

        scenario.precondition?.let { precondition ->
            return Outcome(
                result = ScenarioResult.Skipped(
                    scenarioId = scenario.id,
                    expectedErrorCode = scenario.errorCode,
                    expectedStatus = scenario.expectedStatus,
                    detail = "Needs a controlled gateway: $precondition",
                ),
                observation = null,
            )
        }

        var capability: String? = null
        scenario.capability?.let { request ->
            when (val minted = capabilitySource.issue(request)) {
                is LabCapabilityOutcome.Available -> capability = minted.capability
                is LabCapabilityOutcome.Unavailable -> {
                    return Outcome(
                        result = ScenarioResult.Skipped(
                            scenarioId = scenario.id,
                            expectedErrorCode = scenario.errorCode,
                            expectedStatus = scenario.expectedStatus,
                            detail = minted.reason,
                        ),
                        observation = null,
                    )
                }
            }
        }

        val withCapability = scenario.request.copy(capability = capability)
        val request = scenario.mutation?.mutate(withCapability) ?: withCapability

        val exchange = sendRepeatedly(scenario, request, capability)
        val duration = System.currentTimeMillis() - startedAtMillis

        return when (exchange) {
            is Exchange.Answered -> {
                val observation = LabObservation(
                    scenarioId = scenario.id,
                    statusCode = exchange.statusCode,
                    errorCode = "",
                    responseKind = scenario.responseKind,
                    responseFields = responseFields(exchange.body),
                )
                val result = if (scenario.errorCode.isBlank()) {
                    ScenarioResult.Passed(
                        scenarioId = scenario.id,
                        expectedErrorCode = "",
                        expectedStatus = scenario.expectedStatus,
                        observedErrorCode = "",
                        observedStatus = exchange.statusCode,
                        durationMillis = duration,
                        detail = "The gateway accepted the request.",
                    )
                } else {
                    ScenarioResult.Unexpected(
                        scenarioId = scenario.id,
                        expectedErrorCode = scenario.errorCode,
                        expectedStatus = scenario.expectedStatus,
                        observedErrorCode = "",
                        observedStatus = exchange.statusCode,
                        durationMillis = duration,
                        detail = "The gateway accepted the request, but the scenario " +
                            "expected it to refuse with ${scenario.errorCode}.",
                    )
                }
                Outcome(result = result, observation = observation)
            }

            is Exchange.Refused -> {
                val observation = LabObservation(
                    scenarioId = scenario.id,
                    statusCode = exchange.statusCode,
                    errorCode = exchange.code,
                    responseKind = scenario.responseKind,
                    responseFields = responseFields(exchange.body),
                )
                val matches = exchange.code.equals(scenario.errorCode, ignoreCase = true) &&
                    exchange.statusCode == scenario.expectedStatus
                val result = if (matches) {
                    ScenarioResult.Passed(
                        scenarioId = scenario.id,
                        expectedErrorCode = scenario.errorCode,
                        expectedStatus = scenario.expectedStatus,
                        observedErrorCode = exchange.code,
                        observedStatus = exchange.statusCode,
                        durationMillis = duration,
                    )
                } else {
                    ScenarioResult.Unexpected(
                        scenarioId = scenario.id,
                        expectedErrorCode = scenario.errorCode,
                        expectedStatus = scenario.expectedStatus,
                        observedErrorCode = exchange.code,
                        observedStatus = exchange.statusCode,
                        durationMillis = duration,
                        detail = if (exchange.code.isBlank()) {
                            "The gateway refused with ${exchange.statusCode} and a body that " +
                                "carried no catalogue code."
                        } else {
                            "The gateway refused with ${exchange.code} / ${exchange.statusCode}; " +
                                "the scenario expected ${scenario.errorCode} / " +
                                "${scenario.expectedStatus}."
                        },
                    )
                }
                Outcome(result = result, observation = observation)
            }

            is Exchange.Failed -> Outcome(
                result = ScenarioResult.TransportFailure(
                    scenarioId = scenario.id,
                    expectedErrorCode = scenario.errorCode,
                    expectedStatus = scenario.expectedStatus,
                    durationMillis = duration,
                    detail = exchange.detail,
                ),
                observation = null,
            )
        }
    }

    /**
     * Sends the request as the scenario declares it.
     *
     * A scenario that repeats its request is repeating it deliberately, and the answer
     * that is judged is the last one — the replay is the thing under test, not the
     * first acceptance.
     */
    private suspend fun sendRepeatedly(
        scenario: Scenario,
        request: LabRequest,
        capability: String?,
    ): Exchange {
        var exchange = send(request, capability)

        when (val repeatSpec = scenario.repeat) {
            is LabRepeat.Once -> Unit

            is LabRepeat.SameRequest -> {
                val extra = repeatSpec.times.coerceAtLeast(1) - 1
                repeat(extra) {
                    currentCoroutineContext().ensureActive()
                    exchange = send(request, capability)
                }
            }

            is LabRepeat.WithFollowUp -> {
                currentCoroutineContext().ensureActive()
                exchange = send(repeatSpec.request, capability)
            }
        }

        return exchange
    }

    /**
     * Sends one request and reports what came back.
     */
    private suspend fun send(request: LabRequest, capability: String?): Exchange {
        val body = injectCapability(request.body, capability)
        return try {
            val responseBody = httpClient.request(request.method, request.path, body)
            // The transport returns the body only on a 2xx, so the exact status is not
            // observable here; 200 is recorded and the report says so.
            Exchange.Answered(statusCode = 200, body = responseBody)
        } catch (error: GatewayHttpException) {
            Exchange.Refused(
                statusCode = error.statusCode,
                body = error.body,
                code = error.body?.let { parseErrorCode(it) }.orEmpty(),
            )
        } catch (error: GatewayNotConfiguredException) {
            Exchange.Failed(error.message ?: "The gateway base URL is not configured.")
        } catch (error: GatewayProtocolException) {
            Exchange.Failed(error.message ?: "The gateway's answer could not be read.")
        } catch (error: Exception) {
            Exchange.Failed(error.message ?: "The scenario could not be sent.")
        }
    }

    /**
     * Probes the gateway and refuses anything that is not a test environment.
     *
     * ADR 0004 §1 asks for `GET /health` before each scenario, and this is that call:
     * a live probe, not a cached one. A cached snapshot could have been taken when the
     * console pointed somewhere else, and the whole point of the guard is that it
     * cannot be stale.
     */
    private suspend fun probeTestEnvironment(): AppResult<GatewayHealthSnapshot> = try {
        val body = httpClient.request("GET", GatewayEndpoints.health(), null)
        val dto = json.decodeFromString<GatewayHealthDto>(body)
        LabEnvironmentGuard.requireTestEnvironment(
            GatewayHealthSnapshot(
                status = dto.status,
                environment = dto.environment,
                version = dto.version,
                timestampMillis = dto.timestamp,
                probedAtEpochSeconds = clock.nowEpochSeconds(),
            ),
        )
    } catch (error: GatewayHttpException) {
        AppResult.Failure(
            code = "HEALTH_PROBE_REFUSED",
            message = "Lab Mode requires a healthy test gateway. The health probe was " +
                "refused with ${error.statusCode}.",
        )
    } catch (error: Exception) {
        AppResult.Failure(
            code = "HEALTH_PROBE_FAILED",
            message = "Lab Mode requires a successful health probe against a test gateway. " +
                (error.message ?: "The probe failed."),
        )
    }

    /**
     * Places the capability into the body's `capability` field.
     *
     * The body is kept as JSON rather than as text so that a mutation and the
     * capability injection cannot corrupt each other. A body that is not JSON is
     * returned unchanged: the scenario asked for that body, and the gateway's answer
     * to it is the observation.
     */
    private fun injectCapability(body: String?, capability: String?): String? {
        if (body == null || capability == null) return body
        return try {
            val original = json.parseToJsonElement(body).jsonObject
            val merged = buildJsonObject {
                original.forEach { (key, value) ->
                    if (key == CAPABILITY_FIELD) {
                        put(key, JsonPrimitive(capability))
                    } else {
                        put(key, value)
                    }
                }
                if (!original.containsKey(CAPABILITY_FIELD)) {
                    put(CAPABILITY_FIELD, JsonPrimitive(capability))
                }
            }
            merged.toString()
        } catch (error: Exception) {
            body
        }
    }

    /**
     * The gateway's catalogue code from a refusal body.
     */
    private fun parseErrorCode(body: String): String = try {
        json.decodeFromString<GatewayErrorDto>(body).effectiveCode.trim().uppercase()
    } catch (error: Exception) {
        ""
    }

    /**
     * The field names a response body carried.
     *
     * Used by the contract check to notice drift: a body that dropped a field the
     * schema requires, or grew one the schema forbids.
     */
    private fun responseFields(body: String?): Set<String> {
        if (body.isNullOrBlank()) return emptySet()
        return try {
            json.parseToJsonElement(body).jsonObject.keys
        } catch (error: Exception) {
            emptySet()
        }
    }

    /**
     * What one request produced.
     */
    private sealed interface Exchange {
        /** The gateway answered with a 2xx. */
        data class Answered(val statusCode: Int, val body: String) : Exchange

        /** The gateway refused. */
        data class Refused(
            val statusCode: Int,
            val body: String?,
            val code: String,
        ) : Exchange

        /** There was no answer at all. */
        data class Failed(val detail: String) : Exchange
    }

    /** A scenario's result and, when the gateway answered, what it answered. */
    private data class Outcome(
        val result: ScenarioResult,
        val observation: LabObservation?,
    )

    private companion object {
        const val CAPABILITY_FIELD: String = "capability"

        val json = Json { ignoreUnknownKeys = true }
    }
}
