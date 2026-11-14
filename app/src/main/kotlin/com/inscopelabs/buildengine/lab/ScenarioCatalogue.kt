package com.inscopelabs.buildengine.lab

import com.inscopelabs.buildengine.capability.model.OperationType
import com.inscopelabs.buildengine.core.network.GatewayEndpoints

/**
 * The scenario catalogue, derived from the contract error registry and the fixtures
 * (T5-202).
 *
 * ### Coverage
 *
 * The catalogue holds **at least one scenario for every code in the frozen error
 * catalogue** (gateway/src/errors/catalogue.md, thirty-eight codes), so the unit test
 * the plan asks for — "every registry error code has at least one scenario, including
 * `ENROLL_*`, `AUTH_TIER_NOT_ALLOWED` and `CONFIG_INVALID`" — has something to assert
 * against. [ScenarioCatalogue.missingErrorCodes] is that assertion, made available to
 * the test rather than left implicit.
 *
 * ### Which scenarios the client can provoke
 *
 * A scenario is in one of two groups:
 *
 * - **Client-controllable.** The console can produce the refusal by itself: a malformed
 *   capability, a scope that does not match, a head that has moved, a malformed
 *   idempotency key. These run.
 * - **Fixture-dependent.** The refusal depends on how the gateway is configured — a
 *   halted engine, a repository that is not private, an exhausted rate budget. The
 *   client cannot produce these on demand, so the scenario carries a `precondition`
 *   naming what the gateway must be configured with, and the runner **skips** it with
 *   that precondition quoted. A skipped scenario is never reported as a pass.
 *
 * The report counts the two separately, because "the catalogue passed" and "the
 * catalogue had nothing to run" are very different statements.
 *
 * ### The signature below is a proposal
 *
 * The pre-execution scaffold proposed `all(): List<Scenario>`. That is what this
 * object provides. The stage owns the final shape.
 */
object ScenarioCatalogue {

    // --- The fixture the scenarios are written against -------------------------
    // The values are the ones the contract's own examples use (docs/gateway-contract.md
    // §3.9) and the ones the conformance vectors use, so a scenario and a vector
    // describe the same situation.

    const val PLAN: String = "atlas"
    const val TASK: String = "10008"
    const val BRANCH: String = "task-10008-refactor-parser"
    const val HEAD: String = "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
    const val ORDER: String = "order-7731"
    const val KID: String = "k2"
    const val IDEMPOTENCY_KEY: String = "3b1f0c7a9e5d4a28b6c1d0e9f8a7b6c5"

    /** A key the gateway has never heard of. */
    const val UNKNOWN_KID: String = "k9unknown"

    /**
     * Every scenario in the catalogue.
     */
    fun all(): List<Scenario> = buildList {

        // --- Read paths: the success probes -----------------------------------

        add(
            Scenario(
                id = "read-health",
                title = "GET /health answers and names the environment",
                errorCode = "",
                expectedStatus = 200,
                request = LabRequest(method = "GET", path = GatewayEndpoints.health()),
                responseKind = "health",
                notes = "The Lab's own guard reads this before anything else; the scenario " +
                    "asserts the shape of the answer as well.",
            ),
        )

        add(
            Scenario(
                id = "read-plans",
                title = "GET /plans answers with the plan list",
                errorCode = "",
                expectedStatus = 200,
                request = LabRequest(method = "GET", path = GatewayEndpoints.plans()),
                responseKind = "plans-list",
            ),
        )

        add(
            Scenario(
                id = "read-next-tasks",
                title = "GET /tasks/next answers with the ready tasks",
                errorCode = "",
                expectedStatus = 200,
                request = LabRequest(method = "GET", path = GatewayEndpoints.tasksNext()),
                responseKind = "tasks-next",
            ),
        )

        add(
            Scenario(
                id = "read-branch-head",
                title = "GET /branches/{branch}/head answers with the live head",
                errorCode = "",
                expectedStatus = 200,
                request = LabRequest(
                    method = "GET",
                    path = GatewayEndpoints.branchHead(BRANCH),
                ),
                responseKind = "branch-head",
            ),
        )

        // --- Transport / credential -------------------------------------------

        add(
            fixtureDependent(
                id = "credential-operator-auth",
                title = "A refused operator credential",
                errorCode = "OPERATOR_AUTH",
                expectedStatus = 401,
                notes = "The console presents the credential the operator configured. To see " +
                    "this refusal the gateway must hold a different one.",
            ),
        )

        add(
            fixtureDependent(
                id = "credential-not-accepted",
                title = "A credential the gateway does not accept",
                errorCode = "CREDENTIAL_NOT_ACCEPTED",
                expectedStatus = 400,
                notes = "The gateway must be configured with a credential it refuses.",
            ),
        )

        add(
            fixtureDependent(
                id = "credential-device-not-enrolled",
                title = "A device the gateway does not know",
                errorCode = "DEVICE_NOT_ENROLLED",
                expectedStatus = 401,
                notes = "The device must not be enrolled with the gateway.",
            ),
        )

        add(
            fixtureDependent(
                id = "credential-token-unavailable",
                title = "An upstream token the gateway cannot get",
                errorCode = "TOKEN_UNAVAILABLE",
                expectedStatus = 503,
                notes = "The gateway's upstream platform token must be unavailable.",
            ),
        )

        // --- Capability --------------------------------------------------------

        add(
            Scenario(
                id = "capability-unknown-kid",
                title = "A capability under a key the gateway does not know",
                errorCode = "UNKNOWN_KID",
                expectedStatus = 401,
                request = publishRequest(),
                capability = capabilityRequest(kid = UNKNOWN_KID),
                responseKind = "error",
                notes = "The gateway rejects an unknown key selector before it does any " +
                    "other work (docs/capability-format.md §2).",
            ),
        )

        add(
            Scenario(
                id = "capability-bad-signature",
                title = "A capability whose signature does not match",
                errorCode = "BAD_SIGNATURE",
                expectedStatus = 401,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.TamperedSignature,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-ttl-too-long",
                title = "A capability that lives longer than the gateway allows",
                errorCode = "TTL_TOO_LONG",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.ExtendedTtl,
                responseKind = "error",
                notes = "The gateway's maximum capability TTL is 300 seconds.",
            ),
        )

        add(
            Scenario(
                id = "capability-unknown-op",
                title = "A capability for an operation outside the enumeration",
                errorCode = "UNKNOWN_OP",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.UnknownOperation,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-lowercase-op",
                title = "A capability whose operation is in the wrong case",
                errorCode = "UNKNOWN_OP",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.LowercaseOperation,
                responseKind = "error",
                notes = "The operation enumeration is case-sensitive.",
            ),
        )

        add(
            Scenario(
                id = "capability-branch-mismatch",
                title = "A capability for a branch the request does not name",
                errorCode = "BRANCH_MISMATCH",
                expectedStatus = 403,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.WrongBranch,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-head-moved",
                title = "A request against a head the branch has moved past",
                errorCode = "HEAD_MOVED",
                expectedStatus = 409,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.DriftedHead,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-expired",
                title = "A capability that has already expired",
                errorCode = "CAPABILITY_EXPIRED",
                expectedStatus = 401,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.ExpiredCapability,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-nonce-replayed",
                title = "A capability presented a second time",
                errorCode = "NONCE_REPLAYED",
                expectedStatus = 409,
                request = publishRequest(),
                capability = capabilityRequest(),
                repeat = LabRepeat.SameRequest(times = 2),
                responseKind = "error",
                notes = "The same capability is sent twice on purpose. This is not a retry: " +
                    "the second presentation is the thing under test.",
            ),
        )

        add(
            Scenario(
                id = "capability-extra-field",
                title = "A capability message with one field too many",
                errorCode = "BAD_FIELD_COUNT",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.ExtraField,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-missing-field",
                title = "A capability message with one field missing",
                errorCode = "BAD_FIELD_COUNT",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.MissingField,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-crlf-separators",
                title = "A capability whose fields are separated by CRLF",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.CrlfSeparators,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-trailing-line-feed",
                title = "A capability with a trailing line feed",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.TrailingLineFeed,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-leading-line-feed",
                title = "A capability with a leading line feed",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.LeadingLineFeed,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-invalid-domain-separator",
                title = "A capability for another protocol",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.InvalidDomainSeparator,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-invalid-kid",
                title = "A capability whose key selector is malformed",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.InvalidKid,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-negative-expiry",
                title = "A capability whose expiry is negative",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.NegativeExpiry,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-non-canonical-expiry",
                title = "A capability whose expiry is not canonical",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.NonCanonicalExpiry,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-empty-order",
                title = "A capability with an empty work order",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.EmptyOrder,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-malformed-head",
                title = "A capability whose head is one character short",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.MalformedHead,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-uppercase-head",
                title = "A capability whose head is uppercase hex",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.UppercaseHead,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-short-nonce",
                title = "A capability whose nonce is too short",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.ShortNonce,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-unsafe-engine-branch",
                title = "A capability whose branch carries a path traversal",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.UnsafeEngineBranch,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "capability-non-task-branch",
                title = "A task capability pointing at a branch that is not a task branch",
                errorCode = "BRANCH_INVALID",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.NonTaskBranch,
                responseKind = "error",
                notes = "The conformance vectors expect BRANCH_NOT_TASK for this case; the " +
                    "frozen catalogue's alias table maps that name to BRANCH_INVALID, and " +
                    "the freeze rule says the frozen name is the one to use. The contract " +
                    "check reports the alias if the gateway sends it.",
            ),
        )

        add(
            Scenario(
                id = "capability-engine-op-task-branch",
                title = "An engine capability carrying a task branch",
                errorCode = "BRANCH_MISMATCH",
                expectedStatus = 403,
                request = publishRequest(),
                capability = capabilityRequest(op = OperationType.HALT, branch = BRANCH),
                mutation = LabMutators.EngineOperationOnTaskBranch,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "body-crlf-injection",
                title = "A request body with a CRLF in a text field",
                errorCode = "BAD_ENCODING",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.CrlfInjectionInBody,
                responseKind = "error",
            ),
        )

        // --- Guard layer --------------------------------------------------------

        add(
            fixtureDependent(
                id = "guard-repo-not-private",
                title = "A repository that is not private",
                errorCode = "REPO_NOT_PRIVATE",
                expectedStatus = 403,
                notes = "The gateway's target repository must be public.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-branch-missing",
                title = "A branch that does not exist on the remote",
                errorCode = "BRANCH_MISSING",
                expectedStatus = 404,
                notes = "The gateway's target repository must not have the task's branch.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-ancestry-fail",
                title = "A branch that is not descended from the base",
                errorCode = "ANCESTRY_FAIL",
                expectedStatus = 409,
                notes = "The gateway's target repository must hold a branch that fails the " +
                    "ancestry check.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-delta-empty",
                title = "A branch with nothing in it",
                errorCode = "DELTA_EMPTY",
                expectedStatus = 400,
                notes = "The gateway's target repository must hold an empty branch.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-merge-commits",
                title = "A branch holding merge commits",
                errorCode = "MERGE_COMMITS",
                expectedStatus = 400,
                notes = "The gateway's target repository must hold a branch with merges.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-denied-path",
                title = "A diff that touches a denied path",
                errorCode = "DENIED_PATH",
                expectedStatus = 403,
                notes = "The gateway's target repository must hold a branch that touches a " +
                    "denied path.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-secret-in-diff",
                title = "A diff that contains a secret",
                errorCode = "SECRET_IN_DIFF",
                expectedStatus = 403,
                notes = "The gateway's target repository must hold a branch whose diff " +
                    "matches the secret patterns.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-halt-active",
                title = "A write while the engine is halted",
                errorCode = "HALT_ACTIVE",
                expectedStatus = 409,
                notes = "The engine must be halted before the scenario runs.",
            ),
        )

        add(
            fixtureDependent(
                id = "guard-submission-immutable",
                title = "A submission that has already been accepted",
                errorCode = "SUBMISSION_IMMUTABLE",
                expectedStatus = 409,
                notes = "The gateway must already hold an accepted submission with this id.",
            ),
        )

        // --- Adapter / platform -------------------------------------------------

        add(
            fixtureDependent(
                id = "adapter-unknown-repo",
                title = "A repository the gateway is not configured for",
                errorCode = "UNKNOWN_REPO",
                expectedStatus = 400,
                notes = "The gateway's repository configuration must not cover the " +
                    "requested plan.",
            ),
        )

        add(
            fixtureDependent(
                id = "adapter-rate-budget-low",
                title = "An exhausted rate budget",
                errorCode = "RATE_BUDGET_LOW",
                expectedStatus = 429,
                notes = "The gateway's rate budget must be exhausted.",
            ),
        )

        add(
            fixtureDependent(
                id = "adapter-ruleset-rejected",
                title = "A repository ruleset that refuses the operation",
                errorCode = "RULESET_REJECTED",
                expectedStatus = 403,
                notes = "The gateway's target repository must carry a ruleset that refuses " +
                    "the operation.",
            ),
        )

        add(
            fixtureDependent(
                id = "adapter-reconcile-required",
                title = "An operation that needs a reconcile first",
                errorCode = "RECONCILE_REQUIRED",
                expectedStatus = 409,
                notes = "The gateway's target repository must hold a task that has to be " +
                    "reconciled before the operation is allowed.",
            ),
        )

        add(
            fixtureDependent(
                id = "adapter-network",
                title = "An upstream that cannot be reached",
                errorCode = "NETWORK",
                expectedStatus = 502,
                notes = "The gateway's upstream must be unreachable.",
            ),
        )

        // --- Config / environment -----------------------------------------------

        add(
            fixtureDependent(
                id = "config-invalid",
                title = "A gateway whose configuration is incomplete",
                errorCode = "CONFIG_INVALID",
                expectedStatus = 503,
                notes = "The gateway must be configured so that its routes refuse work.",
            ),
        )

        add(
            fixtureDependent(
                id = "config-environment-mismatch",
                title = "A gateway that reports a different environment",
                errorCode = "ENVIRONMENT_MISMATCH",
                expectedStatus = 403,
                notes = "The gateway must report an environment other than the one the " +
                    "console expects. The Lab's own guard refuses before this scenario " +
                    "could run, so it needs a gateway the console can talk to but that " +
                    "disagrees about the environment.",
            ),
        )

        // --- Enrolment / attestation ---------------------------------------------

        add(
            Scenario(
                id = "enroll-challenge-invalid",
                title = "An enrolment with a challenge the gateway never issued",
                errorCode = "ENROLL_CHALLENGE_INVALID",
                expectedStatus = 400,
                request = LabRequest(
                    method = "POST",
                    path = GatewayEndpoints.enroll(),
                    body = enrolBody(challenge = "00000000000000000000000000000000"),
                ),
                responseKind = "error",
                notes = "The challenge is one the gateway did not issue, so it is unknown, " +
                    "expired or already used.",
            ),
        )

        add(
            Scenario(
                id = "enroll-attestation-invalid",
                title = "An enrolment whose attestation does not match",
                errorCode = "ENROLL_ATTESTATION_INVALID",
                expectedStatus = 400,
                request = LabRequest(
                    method = "POST",
                    path = GatewayEndpoints.enroll(),
                    body = enrolBody(
                        challenge = "00000000000000000000000000000000",
                        attestation = "not-a-real-attestation-chain",
                    ),
                ),
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "enroll-auth-tier-not-allowed",
                title = "An enrolment at a tier the environment does not accept",
                errorCode = "AUTH_TIER_NOT_ALLOWED",
                expectedStatus = 403,
                request = LabRequest(
                    method = "POST",
                    path = GatewayEndpoints.enroll(),
                    body = enrolBody(
                        challenge = "00000000000000000000000000000000",
                        authTier = "in_app_confirm",
                    ),
                ),
                responseKind = "error",
                notes = "Whether this tier is refused depends on the gateway's tier policy " +
                    "for the environment it is deployed in.",
            ),
        )

        // --- Fail-closed stub ----------------------------------------------------

        add(
            fixtureDependent(
                id = "stub-not-implemented",
                title = "An operation the gateway has not implemented",
                errorCode = "NOT_IMPLEMENTED",
                expectedStatus = 501,
                notes = "The gateway build must leave this operation as a fail-closed stub. " +
                    "The published stub answers 501 and never a 2xx (ADR 0002).",
            ),
        )

        // --- Operation idempotency -----------------------------------------------

        add(
            Scenario(
                id = "idempotency-key-invalid",
                title = "A write whose idempotency key is malformed",
                errorCode = "IDEMPOTENCY_KEY_INVALID",
                expectedStatus = 400,
                request = publishRequest(),
                capability = capabilityRequest(),
                mutation = LabMutators.InvalidIdempotencyKey,
                responseKind = "error",
            ),
        )

        add(
            Scenario(
                id = "idempotency-key-reused",
                title = "A write that reuses a key with a different fingerprint",
                errorCode = "IDEMPOTENCY_KEY_REUSED",
                expectedStatus = 409,
                request = publishRequest(),
                capability = capabilityRequest(),
                repeat = LabRepeat.WithFollowUp(
                    request = publishRequest(idempotencyKey = IDEMPOTENCY_KEY, head = HEAD).copy(
                        body = publishBody(idempotencyKey = IDEMPOTENCY_KEY, head = OTHER_HEAD),
                    ),
                ),
                responseKind = "error",
                notes = "The same key is presented twice with a different body. The second " +
                    "presentation must be refused and must execute nothing.",
            ),
        )

        add(
            Scenario(
                id = "operation-not-found",
                title = "A lookup for an operation the gateway does not hold",
                errorCode = "OPERATION_NOT_FOUND",
                expectedStatus = 404,
                request = LabRequest(
                    method = "GET",
                    path = GatewayEndpoints.operationLookup("00000000000000000000000000000000"),
                ),
                responseKind = "error",
            ),
        )
    }

    /**
     * The codes in the frozen catalogue that no scenario covers.
     *
     * The plan asks for a unit test that asserts every registry error code has at
     * least one scenario. This is that assertion, exposed so the test can be written
     * against it rather than by counting strings twice.
     */
    fun missingErrorCodes(): List<String> {
        val covered = all()
            .map { it.errorCode.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return ContractCheck.KNOWN_ERROR_CODES.filterNot { it in covered }.sorted()
    }

    /**
     * The scenario with the given id, if the catalogue holds one.
     */
    fun byId(id: String): Scenario? = all().firstOrNull { it.id == id }

    // --- Builders --------------------------------------------------------------

    /**
     * A publish request body.
     *
     * The `capability` field is a placeholder: the runner replaces it with the
     * capability it minted, or with the mutation's version of it.
     */
    private fun publishBody(
        idempotencyKey: String = IDEMPOTENCY_KEY,
        head: String = HEAD,
    ): String = """{"plan":"$PLAN","task":"$TASK","branch":"$BRANCH","head":"$head",""" +
        """"idempotencyKey":"$idempotencyKey","capability":""}"""

    private fun publishRequest(
        idempotencyKey: String = IDEMPOTENCY_KEY,
        head: String = HEAD,
    ): LabRequest = LabRequest(
        method = "POST",
        path = GatewayEndpoints.publish(TASK),
        body = publishBody(idempotencyKey = idempotencyKey, head = head),
    )

    /**
     * The capability a publish scenario needs.
     */
    private fun capabilityRequest(
        op: OperationType = OperationType.PUBLISH,
        branch: String = BRANCH,
        head: String = HEAD,
        kid: String = KID,
    ): LabCapabilityRequest = LabCapabilityRequest(
        op = op,
        branch = branch,
        head = head,
        plan = PLAN,
        task = TASK,
        order = ORDER,
        kid = kid,
    )

    /**
     * An enrolment body.
     */
    private fun enrolBody(
        challenge: String,
        attestation: String = "not-a-real-attestation-chain",
        authTier: String = "hardware_confirm",
    ): String = """{"plan":"$PLAN","task":"$TASK","branch":"$BRANCH","head":"$HEAD",""" +
        """"challenge":"$challenge","attestation":"$attestation","authTier":"$authTier",""" +
        """"kid":"$KID","capability":""}"""

    /**
     * A scenario the client cannot provoke on its own.
     */
    private fun fixtureDependent(
        id: String,
        title: String,
        errorCode: String,
        expectedStatus: Int,
        notes: String,
    ): Scenario = Scenario(
        id = id,
        title = title,
        errorCode = errorCode,
        expectedStatus = expectedStatus,
        request = publishRequest(),
        capability = capabilityRequest(),
        precondition = notes,
        notes = notes,
    )

    /** A second, different head — used to change a request's fingerprint. */
    private const val OTHER_HEAD: String = "1111111111111111111111111111111111111111"
}
