package com.inscopelabs.buildengine.lab

/**
 * The typed result of one Lab scenario.
 *
 * **A result never carries a capability, a signature or a nonce.** The Lab sends
 * capabilities; the result records what the gateway answered. A result that quoted the
 * capability would put a live grant into a report that the operator can share off the
 * device (ADR 0004 §4), so the fields below are the whole of it: what was expected,
 * what came back, and the status.
 *
 * The four states are kept apart on purpose:
 *
 * - [Passed] — the gateway refused exactly as the scenario declared.
 * - [Unexpected] — the gateway answered, but not the way the scenario declared. This is
 *   the interesting failure: a gateway that answers `200` where the contract says
 *   `409` has a conformance problem, and it must not read as a pass.
 * - [TransportFailure] — there was no answer at all. The request never got one, or the
 *   answer could not be read.
 * - [Skipped] — the scenario was not run. A precondition was unmet, the Lab signer was
 *   not wired, Lab Mode was off, or the run was cancelled.
 */
sealed interface ScenarioResult {

    /** The scenario this result belongs to. */
    val scenarioId: String

    /** The error code the scenario declared it expected. Empty when it expected success. */
    val expectedErrorCode: String

    /** The HTTP status the scenario declared it expected. */
    val expectedStatus: Int

    /** The error code the gateway actually returned. Empty when it returned none. */
    val observedErrorCode: String

    /** The HTTP status the gateway actually returned. `null` when there was no answer. */
    val observedStatus: Int?

    /** How long the scenario took, in milliseconds. */
    val durationMillis: Long

    /** Why the scenario ended the way it did. Never contains a capability. */
    val detail: String

    /**
     * The gateway refused exactly as the scenario declared.
     */
    data class Passed(
        override val scenarioId: String,
        override val expectedErrorCode: String,
        override val expectedStatus: Int,
        override val observedErrorCode: String,
        override val observedStatus: Int,
        override val durationMillis: Long,
        override val detail: String = "",
    ) : ScenarioResult

    /**
     * The gateway answered, but not the way the scenario declared.
     *
     * Either the error code is a different one, or the status is. A different code is
     * a different refusal and the difference matters; a different status for the same
     * code is a contract drift in the other direction.
     */
    data class Unexpected(
        override val scenarioId: String,
        override val expectedErrorCode: String,
        override val expectedStatus: Int,
        override val observedErrorCode: String,
        override val observedStatus: Int?,
        override val durationMillis: Long,
        override val detail: String,
    ) : ScenarioResult

    /**
     * There was no answer to judge.
     *
     * The request never reached a gateway that could answer, or the answer could not be
     * read. This is never a pass: an unanswered scenario has proved nothing.
     */
    data class TransportFailure(
        override val scenarioId: String,
        override val expectedErrorCode: String,
        override val expectedStatus: Int,
        override val observedErrorCode: String = "",
        override val observedStatus: Int? = null,
        override val durationMillis: Long,
        override val detail: String,
    ) : ScenarioResult

    /**
     * The scenario was not run.
     *
     * `detail` says why: the precondition the gateway did not meet, the lab signer
     * that is not wired, Lab Mode being off, or the run being cancelled.
     */
    data class Skipped(
        override val scenarioId: String,
        override val expectedErrorCode: String,
        override val expectedStatus: Int,
        override val durationMillis: Long = 0L,
        override val detail: String,
    ) : ScenarioResult {
        override val observedErrorCode: String = ""
        override val observedStatus: Int? = null
    }

    /**
     * Whether this result counts as the scenario having behaved as declared.
     *
     * Only [Passed] does. A skipped scenario is not a pass and a transport failure is
     * not a pass, and the report counts them separately.
     */
    val isPass: Boolean
        get() = this is Passed
}
