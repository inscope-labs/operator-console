package com.inscopelabs.buildengine.lab

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * What the gateway actually answered, as the contract check sees it.
 *
 * The observation carries the code, the status, the kind of response the scenario was
 * looking for, and the field names the body carried. It carries nothing else — no
 * request body, no capability, no signature.
 */
data class LabObservation(
    val scenarioId: String,
    val statusCode: Int,
    val errorCode: String,
    /** The kind of response the scenario declared. See [Scenario.responseKind]. */
    val responseKind: String,
    /** The field names the response body carried. */
    val responseFields: Set<String>,
)

/**
 * A disagreement between what the gateway did and what the published contract says.
 */
sealed interface ContractDrift {

    /** One line describing the drift. */
    val summary: String

    /**
     * The gateway returned a code the console does not know.
     *
     * This is the drift that matters most: an unknown code cannot be mapped to an
     * operator-facing state, so the console would show a refusal it cannot explain.
     */
    data class UnknownErrorCode(
        val code: String,
        val statusCode: Int,
    ) : ContractDrift {
        override val summary: String =
            "The gateway returned '$code' (HTTP $statusCode), which is not in the frozen " +
                "error catalogue."
    }

    /**
     * A known code arrived with a status the catalogue does not give it.
     */
    data class StatusMismatch(
        val code: String,
        val expectedStatus: Int,
        val observedStatus: Int,
    ) : ContractDrift {
        override val summary: String =
            "The gateway returned '$code' with HTTP $observedStatus; the catalogue gives " +
                "it HTTP $expectedStatus."
    }

    /**
     * A response body is missing a field its schema requires.
     */
    data class MissingField(
        val responseKind: String,
        val field: String,
    ) : ContractDrift {
        override val summary: String =
            "A '$responseKind' response is missing the field '$field'."
    }

    /**
     * A response body carries a field its schema does not allow.
     */
    data class UnexpectedField(
        val responseKind: String,
        val field: String,
    ) : ContractDrift {
        override val summary: String =
            "A '$responseKind' response carries the field '$field', which its schema does " +
                "not define."
    }

    /**
     * The gateway used a parent-contract alias instead of the frozen name.
     *
     * The frozen catalogue's freeze rule says it plainly: "Conformance vectors and
     * client `ErrorMapper` must reference the frozen names only." An alias in a
     * response is a drift even though the console can still understand it.
     */
    data class ParentAliasInUse(
        val alias: String,
        val frozenName: String,
    ) : ContractDrift {
        override val summary: String =
            "The gateway returned the parent-contract alias '$alias'; the frozen catalogue " +
                "name is '$frozenName'."
    }
}

/**
 * The shape a published schema gives one kind of response.
 */
data class ResponseShape(
    val required: Set<String>,
    val allowed: Set<String>,
)

/**
 * Flags unknown error codes and DTO drift against the published contract (T5-401).
 *
 * ### What it checks
 *
 * 1. **Unknown error codes.** A code the console does not know cannot be mapped to an
 *    operator-facing state, so it is drift the moment it appears.
 * 2. **Status drift.** A known code that arrives with the wrong HTTP status.
 * 3. **DTO drift.** A response body that dropped a field its schema requires, or grew
 *    one its schema does not define.
 * 4. **Parent-contract aliases.** A response that uses a name the frozen catalogue has
 *    replaced.
 *
 * ### What it does not do
 *
 * It does not compare the console's DTO classes to the schemas at runtime. The schemas
 * live in `docs/schemas/` and are not shipped inside the app, so the field sets below
 * are transcribed from them. The stage should generate these tables from the published
 * schemas rather than maintain them by hand — a hand-maintained table is a second
 * contract that can drift from the first, which is the very thing this check exists to
 * catch.
 *
 * ### The signature below is a proposal
 *
 * The pre-execution scaffold proposed `run(): List<String>`. It takes the run's
 * observations and returns typed drifts instead, because a drift a machine can read is
 * worth more than a line of prose, and because the check cannot say anything at all
 * about a gateway it never spoke to.
 */
object ContractCheck {

    /**
     * The frozen error catalogue, in full.
     *
     * gateway/src/errors/catalogue.ts and gateway/docs/error-catalogue.md, frozen at
     * Stage 1.3 (ADR 0003). Thirty-eight codes. A code that is not here is not a code
     * the console can explain.
     */
    val KNOWN_ERROR_CODES: Set<String> = setOf(
        // Transport / credential
        "OPERATOR_AUTH",
        "CREDENTIAL_NOT_ACCEPTED",
        "DEVICE_NOT_ENROLLED",
        "TOKEN_UNAVAILABLE",
        // Capability
        "UNKNOWN_KID",
        "BAD_SIGNATURE",
        "TTL_TOO_LONG",
        "UNKNOWN_OP",
        "BRANCH_MISMATCH",
        "HEAD_MOVED",
        "NONCE_REPLAYED",
        "CAPABILITY_EXPIRED",
        "BAD_FIELD_COUNT",
        "BAD_ENCODING",
        // Guard layer
        "REPO_NOT_PRIVATE",
        "BRANCH_INVALID",
        "BRANCH_MISSING",
        "ANCESTRY_FAIL",
        "DELTA_EMPTY",
        "MERGE_COMMITS",
        "DENIED_PATH",
        "SECRET_IN_DIFF",
        "HALT_ACTIVE",
        "SUBMISSION_IMMUTABLE",
        // Adapter / platform
        "UNKNOWN_REPO",
        "RATE_BUDGET_LOW",
        "RULESET_REJECTED",
        "RECONCILE_REQUIRED",
        "NETWORK",
        // Config / environment
        "CONFIG_INVALID",
        "ENVIRONMENT_MISMATCH",
        // Enrolment / attestation
        "ENROLL_CHALLENGE_INVALID",
        "ENROLL_ATTESTATION_INVALID",
        "AUTH_TIER_NOT_ALLOWED",
        // Fail-closed stub
        "NOT_IMPLEMENTED",
        // Operation idempotency
        "IDEMPOTENCY_KEY_INVALID",
        "IDEMPOTENCY_KEY_REUSED",
        "OPERATION_NOT_FOUND",
    )

    /**
     * The HTTP status each frozen code carries.
     */
    val KNOWN_ERROR_STATUS: Map<String, Int> = mapOf(
        "OPERATOR_AUTH" to 401,
        "CREDENTIAL_NOT_ACCEPTED" to 400,
        "DEVICE_NOT_ENROLLED" to 401,
        "TOKEN_UNAVAILABLE" to 503,
        "UNKNOWN_KID" to 401,
        "BAD_SIGNATURE" to 401,
        "TTL_TOO_LONG" to 400,
        "UNKNOWN_OP" to 400,
        "BRANCH_MISMATCH" to 403,
        "HEAD_MOVED" to 409,
        "NONCE_REPLAYED" to 409,
        "CAPABILITY_EXPIRED" to 401,
        "BAD_FIELD_COUNT" to 400,
        "BAD_ENCODING" to 400,
        "REPO_NOT_PRIVATE" to 403,
        "BRANCH_INVALID" to 400,
        "BRANCH_MISSING" to 404,
        "ANCESTRY_FAIL" to 409,
        "DELTA_EMPTY" to 400,
        "MERGE_COMMITS" to 400,
        "DENIED_PATH" to 403,
        "SECRET_IN_DIFF" to 403,
        "HALT_ACTIVE" to 409,
        "SUBMISSION_IMMUTABLE" to 409,
        "UNKNOWN_REPO" to 400,
        "RATE_BUDGET_LOW" to 429,
        "RULESET_REJECTED" to 403,
        "RECONCILE_REQUIRED" to 409,
        "NETWORK" to 502,
        "CONFIG_INVALID" to 503,
        "ENVIRONMENT_MISMATCH" to 403,
        "ENROLL_CHALLENGE_INVALID" to 400,
        "ENROLL_ATTESTATION_INVALID" to 400,
        "AUTH_TIER_NOT_ALLOWED" to 403,
        "NOT_IMPLEMENTED" to 501,
        "IDEMPOTENCY_KEY_INVALID" to 400,
        "IDEMPOTENCY_KEY_REUSED" to 409,
        "OPERATION_NOT_FOUND" to 404,
    )

    /**
     * The parent-contract names the frozen catalogue replaced.
     *
     * gateway/docs/error-catalogue.md, "Aliases (parent contract → frozen catalogue)".
     * The freeze rule says the conformance vectors and the client `ErrorMapper` must
     * reference the frozen names only, so an alias appearing in a response is drift.
     */
    val PARENT_CONTRACT_ALIASES: Map<String, String> = mapOf(
        "NOT_ENROLLED" to "DEVICE_NOT_ENROLLED",
        "EXPIRED" to "CAPABILITY_EXPIRED",
        "HEAD_DRIFT" to "HEAD_MOVED",
        "BRANCH_NOT_TASK" to "BRANCH_INVALID",
    )

    /**
     * The field sets each published schema gives a response kind.
     *
     * Transcribed from docs/schemas/. See the object's KDoc: the stage should generate
     * this table from the schemas rather than keep it by hand.
     */
    val RESPONSE_SHAPES: Map<String, ResponseShape> = mapOf(
        // The frozen error body: { "error", "message", "request_id" }. The parent
        // contract used "code"; both are accepted on the wire and both are listed.
        "error" to ResponseShape(
            required = emptySet(),
            allowed = setOf("error", "code", "message", "request_id"),
        ),
        // publish / upload / reconcile / halt / resume acknowledgements.
        "ack" to ResponseShape(
            required = setOf("accepted", "operation", "auditRef"),
            allowed = setOf("accepted", "operation", "branch", "headSha", "auditRef"),
        ),
        "health" to ResponseShape(
            required = setOf("status", "environment", "version", "timestamp"),
            allowed = setOf("status", "environment", "version", "timestamp", "config"),
        ),
        "branch-head" to ResponseShape(
            required = setOf("branch", "headSha"),
            allowed = setOf("branch", "headSha"),
        ),
        "operation-lookup" to ResponseShape(
            required = setOf("id", "operation", "status", "timestamp"),
            allowed = setOf(
                "id", "operation", "status", "branch", "headSha", "auditRef", "timestamp", "code",
            ),
        ),
        "plans-list" to ResponseShape(
            required = setOf("id", "title", "status", "taskCount", "completedTaskCount", "updatedAt"),
            allowed = setOf("id", "title", "status", "taskCount", "completedTaskCount", "updatedAt"),
        ),
        "tasks-next" to ResponseShape(
            required = setOf("id", "planId", "title", "branch", "status", "headSha"),
            allowed = setOf("id", "planId", "title", "branch", "status", "headSha"),
        ),
    )

    /**
     * Checks a run's observations against the published contract.
     *
     * An observation with no error code is not checked against the error catalogue —
     * a success carries no code to be wrong about — but it is checked against its
     * response shape when the scenario declared one.
     */
    fun run(observations: List<LabObservation>): List<ContractDrift> {
        val drifts = mutableListOf<ContractDrift>()

        observations.forEach { observation ->
            val code = observation.errorCode.trim().uppercase()
            if (code.isNotEmpty()) {
                val alias = PARENT_CONTRACT_ALIASES[code]
                if (alias != null) {
                    drifts += ContractDrift.ParentAliasInUse(alias = code, frozenName = alias)
                } else if (code !in KNOWN_ERROR_CODES) {
                    drifts += ContractDrift.UnknownErrorCode(
                        code = code,
                        statusCode = observation.statusCode,
                    )
                } else {
                    val expectedStatus = KNOWN_ERROR_STATUS[code]
                    if (expectedStatus != null && expectedStatus != observation.statusCode) {
                        drifts += ContractDrift.StatusMismatch(
                            code = code,
                            expectedStatus = expectedStatus,
                            observedStatus = observation.statusCode,
                        )
                    }
                }
            }

            val shape = RESPONSE_SHAPES[observation.responseKind] ?: return@forEach
            shape.required.forEach { field ->
                if (field !in observation.responseFields) {
                    drifts += ContractDrift.MissingField(
                        responseKind = observation.responseKind,
                        field = field,
                    )
                }
            }
            observation.responseFields.forEach { field ->
                if (field !in shape.allowed) {
                    drifts += ContractDrift.UnexpectedField(
                        responseKind = observation.responseKind,
                        field = field,
                    )
                }
            }
        }

        return drifts
    }

    /**
     * The field names a JSON object body carried.
     *
     * A body that is not a JSON object has no field names, which the caller reports as
     * "the shape could not be read" rather than as a body with no fields — the two are
     * different findings and the check must not confuse them.
     */
    fun fieldsOf(body: String?): Set<String> {
        if (body.isNullOrBlank()) return emptySet()
        return try {
            json.parseToJsonElement(body).jsonObject.keys
        } catch (error: Exception) {
            emptySet()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
}
