package com.inscopelabs.buildengine.core.network

import com.inscopelabs.buildengine.core.network.dto.GatewayErrorDto
import com.inscopelabs.buildengine.core.result.AppResult
import kotlinx.serialization.json.Json

/**
 * The state a gateway refusal falls into.
 *
 * docs/gateway-contract.md §4.2: the console maps incoming errors into one of four
 * primary UI states — **Scope Refused**, **Replayed**, **Head Drifted** and
 * **Halt Active** — plus the general authentication, format and guard errors.
 *
 * The four are different situations with different remedies, so they are never
 * collapsed into one "the gateway refused":
 *
 * - **Scope Refused** — the capability did not cover what was asked for. The
 *   operator must review the scope, not re-mint the same thing.
 * - **Replayed** — the nonce was already spent. A fresh capability, with a fresh
 *   biometric confirmation.
 * - **Head Drifted** — the branch moved after the operator looked at it. The
 *   operator has to look at the new head before approving again.
 * - **Halt Active** — the engine is halted. Writes are locked until somebody
 *   issues a `RESUME`.
 */
enum class GatewayFailureState(val code: String) {
    /** Distinct failure vocabulary per Guide §4.3 / Stage 2.4 (T2-402). */
    NOT_CONFIGURED("not_configured"),
    UNREACHABLE("unreachable"),
    TLS_PINNING("tls_pinning"),
    AUTH("auth"),
    STALE("stale"),
    POLICY_REFUSED("policy_refused"),

    // Specific refusal and protocol states
    SCOPE_REFUSED("scope_refused"),
    REPLAYED("replayed"),
    HEAD_DRIFTED("head_drifted"),
    HALT_ACTIVE("halt_active"),
    AUTHENTICATION("authentication"),
    FORMAT("format"),
    GUARD_FAILED("guard_failed"),
    ENVIRONMENT_MISMATCH("environment_mismatch"),
    NOT_FOUND("not_found"),
    CONFLICT("conflict"),
    RATE_LIMITED("rate_limited"),
    GATEWAY_ERROR("gateway_error"),
    UNKNOWN("unknown"),
}

/**
 * Maps the gateway's error catalogue onto UI states.
 *
 * Design review §12, Appendix B.3: the V5-V8 rejections (scope mismatch, replayed
 * capability, drifted head, active halt) must surface clearly and separately.
 *
 * ### The gateway's reason is the contract; the status is the fallback
 *
 * The gateway states which check it failed in the `code` of its error body
 * (docs/gateway-contract.md §4.1), and that reason is the primary signal. The HTTP
 * status is the coarse class, and on its own it cannot tell a replayed capability
 * from a drifted head — both are `409 Conflict` — which is exactly why the reason
 * has to travel in the body.
 *
 * The failure the console produces carries the **state** in its `code`, because that
 * is what the UI branches on, and the gateway's own code in its `message`, so
 * nothing the gateway said is lost: a refusal reads
 * `HEAD_DRIFT: the branch has advanced since the capability was minted`.
 *
 * A code the console does not recognise is surfaced unchanged rather than
 * flattened: the gateway knows more about why it refused than the console does.
 */
class ErrorMapper {

    /**
     * Maps a gateway refusal onto the failure the console shows.
     */
    fun map(statusCode: Int, body: String?): AppResult.Failure {
        val error = parse(body)
        val gatewayCode = error?.effectiveCode?.trim()?.takeIf { it.isNotEmpty() }
        val state = stateFor(gatewayCode ?: "", statusCode)

        val detail = error?.message?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultMessage(state)

        val message = if (gatewayCode != null) "$gatewayCode: $detail" else detail

        return AppResult.Failure(code = state.code, message = message)
    }

    /**
     * The stable code for a status code alone.
     *
     * This is the fallback for a refusal that arrived without a readable body — a
     * proxy error page, an empty 502, a body the console could not parse. It is a
     * class, not a verdict: `409` is a conflict because the status cannot say
     * whether it was a replay or a drift.
     */
    fun mapStatusCode(statusCode: Int): String = stateFor(null, statusCode).code

    /**
     * The state a gateway code falls into.
     *
     * An unrecognised code is [GatewayFailureState.UNKNOWN] rather than a guess.
     * The registry is the gateway's, and the console does not get to widen it.
     */
    fun stateFor(gatewayCode: String?, statusCode: Int? = null): GatewayFailureState {
        val code = gatewayCode?.trim()?.uppercase()?.substringBefore(':')?.trim()
        if (!code.isNullOrEmpty()) {
            val matched = GATEWAY_CODE_STATES[code]
            if (matched != null) return matched
            return GatewayFailureState.UNKNOWN
        }
        return statusCode?.let { stateForStatus(it) } ?: GatewayFailureState.UNKNOWN
    }

    /**
     * The failure for an answer the console could not read.
     *
     * The gateway accepted the request and then answered in a way the console does
     * not understand. That is not a refusal, and the operator needs to see it as
     * something else.
     */
    fun mapProtocolFailure(message: String): AppResult.Failure =
        AppResult.Failure(code = GatewayFailureState.UNKNOWN.code, message = message)

    /**
     * Reads the gateway's error body, if it is one.
     *
     * A body that is absent, empty or not the documented shape is not an error in
     * itself: it means the console has nothing better than the status to go on.
     */
    private fun parse(body: String?): GatewayErrorDto? {
        if (body.isNullOrBlank()) return null
        return try {
            json.decodeFromString<GatewayErrorDto>(body)
        } catch (error: Exception) {
            null
        }
    }

    private fun stateForStatus(statusCode: Int): GatewayFailureState = when (statusCode) {
        400 -> GatewayFailureState.FORMAT
        401 -> GatewayFailureState.AUTHENTICATION
        403 -> GatewayFailureState.SCOPE_REFUSED
        404 -> GatewayFailureState.NOT_FOUND
        409 -> GatewayFailureState.CONFLICT
        422 -> GatewayFailureState.GUARD_FAILED
        423 -> GatewayFailureState.HALT_ACTIVE
        429 -> GatewayFailureState.RATE_LIMITED
        in 500..599 -> GatewayFailureState.GATEWAY_ERROR
        else -> GatewayFailureState.UNKNOWN
    }

    private fun defaultMessage(state: GatewayFailureState): String = when (state) {
        GatewayFailureState.NOT_CONFIGURED ->
            "the gateway is not configured — set a base URL in Settings before connecting"
        GatewayFailureState.UNREACHABLE ->
            "the gateway could not be reached — check the host, network, and DNS"
        GatewayFailureState.TLS_PINNING ->
            "certificate pinning or TLS verification failed — check host certificate and pin set"
        GatewayFailureState.AUTH ->
            "the gateway did not accept the console's credential"
        GatewayFailureState.STALE ->
            "the branch state or capability has expired or drifted — refresh before retrying"
        GatewayFailureState.POLICY_REFUSED ->
            "the gateway policy checks refused the operation"
        GatewayFailureState.SCOPE_REFUSED -> "the gateway refused the scope of the operation"
        GatewayFailureState.REPLAYED -> "the capability has already been used"
        GatewayFailureState.HEAD_DRIFTED -> "the branch has moved since the capability was minted"
        GatewayFailureState.HALT_ACTIVE -> "the engine is halted"
        GatewayFailureState.AUTHENTICATION -> "the gateway did not accept the console's credential"
        GatewayFailureState.FORMAT -> "the gateway rejected the request as malformed"
        GatewayFailureState.GUARD_FAILED -> "the gateway's guard checks refused the operation"
        GatewayFailureState.ENVIRONMENT_MISMATCH -> "the gateway is configured for a different environment"
        GatewayFailureState.NOT_FOUND -> "the gateway has no such resource"
        GatewayFailureState.CONFLICT -> "the operation conflicts with the gateway's current state"
        GatewayFailureState.RATE_LIMITED -> "the gateway is rate limiting the console"
        GatewayFailureState.GATEWAY_ERROR -> "the gateway failed to handle the request"
        GatewayFailureState.UNKNOWN -> "the gateway refused the operation"
    }

    /** Failure when the console has no real gateway URL configured. */
    fun mapNotConfigured(message: String? = null): AppResult.Failure =
        AppResult.Failure(
            code = GatewayFailureState.NOT_CONFIGURED.code,
            message = message ?: defaultMessage(GatewayFailureState.NOT_CONFIGURED),
        )

    /** Failure when the transport cannot reach the host (DNS, TCP, timeout). */
    fun mapUnreachable(message: String? = null): AppResult.Failure =
        AppResult.Failure(
            code = GatewayFailureState.UNREACHABLE.code,
            message = message ?: defaultMessage(GatewayFailureState.UNREACHABLE),
        )

    /** Failure when certificate pinning, TLS handshake, or pin set expiry fails (T2-402). */
    fun mapTlsPinning(message: String? = null): AppResult.Failure =
        AppResult.Failure(
            code = GatewayFailureState.TLS_PINNING.code,
            message = message ?: defaultMessage(GatewayFailureState.TLS_PINNING),
        )

    /** Failure when operator credential is missing, invalid, or expired (T2-402). */
    fun mapAuth(message: String? = null): AppResult.Failure =
        AppResult.Failure(
            code = GatewayFailureState.AUTH.code,
            message = message ?: defaultMessage(GatewayFailureState.AUTH),
        )

    /** Failure when capability or target branch HEAD has moved or expired (T2-402). */
    fun mapStale(message: String? = null): AppResult.Failure =
        AppResult.Failure(
            code = GatewayFailureState.STALE.code,
            message = message ?: defaultMessage(GatewayFailureState.STALE),
        )

    /** Failure when gateway guards or engine policy refuse the operation (T2-402). */
    fun mapPolicyRefused(message: String? = null): AppResult.Failure =
        AppResult.Failure(
            code = GatewayFailureState.POLICY_REFUSED.code,
            message = message ?: defaultMessage(GatewayFailureState.POLICY_REFUSED),
        )

    /** Maps a transport exception to the appropriate distinct failure state (T2-402). */
    fun mapException(throwable: Throwable): AppResult.Failure = when (throwable) {
        is javax.net.ssl.SSLPeerUnverifiedException -> mapTlsPinning(throwable.message)
        is GatewayNotConfiguredException -> mapNotConfigured(throwable.message)
        is java.net.UnknownHostException -> mapUnreachable("the gateway host could not be resolved: ${throwable.message}")
        is java.io.IOException -> mapUnreachable(throwable.message)
        is GatewayHttpException -> map(throwable.statusCode, throwable.body)
        is GatewayProtocolException -> mapProtocolFailure(throwable.message ?: "the gateway response was malformed")
        else -> AppResult.Failure(
            code = GatewayFailureState.UNKNOWN.code,
            message = throwable.message ?: "unknown gateway communication failure",
        )
    }

    companion object {
        // -- the gateway's error registry (docs/gateway-contract.md §4.1) --

        const val GATEWAY_UNKNOWN_KID: String = "UNKNOWN_KID"
        const val GATEWAY_BAD_SIGNATURE: String = "BAD_SIGNATURE"
        const val GATEWAY_OPERATOR_AUTH: String = "OPERATOR_AUTH"
        const val GATEWAY_CREDENTIAL_NOT_ACCEPTED: String = "CREDENTIAL_NOT_ACCEPTED"
        const val GATEWAY_NOT_ENROLLED: String = "NOT_ENROLLED"
        const val GATEWAY_DEVICE_NOT_ENROLLED: String = "DEVICE_NOT_ENROLLED"
        const val GATEWAY_TOKEN_UNAVAILABLE: String = "TOKEN_UNAVAILABLE"
        const val GATEWAY_BRANCH_MISMATCH: String = "BRANCH_MISMATCH"
        const val GATEWAY_ENVIRONMENT_MISMATCH: String = "ENVIRONMENT_MISMATCH"
        const val GATEWAY_EXPIRED: String = "EXPIRED"
        const val GATEWAY_CAPABILITY_EXPIRED: String = "CAPABILITY_EXPIRED"
        const val GATEWAY_TTL_TOO_LONG: String = "TTL_TOO_LONG"
        const val GATEWAY_UNKNOWN_OP: String = "UNKNOWN_OP"
        const val GATEWAY_BAD_FIELD_COUNT: String = "BAD_FIELD_COUNT"
        const val GATEWAY_BAD_ENCODING: String = "BAD_ENCODING"
        const val GATEWAY_BRANCH_NOT_TASK: String = "BRANCH_NOT_TASK"
        const val GATEWAY_BRANCH_INVALID: String = "BRANCH_INVALID"
        const val GATEWAY_BRANCH_MISSING: String = "BRANCH_MISSING"
        const val GATEWAY_NONCE_REPLAYED: String = "NONCE_REPLAYED"
        const val GATEWAY_HEAD_DRIFT: String = "HEAD_DRIFT"
        const val GATEWAY_HEAD_MOVED: String = "HEAD_MOVED"
        const val GATEWAY_HALT_ACTIVE: String = "HALT_ACTIVE"
        const val GATEWAY_GUARD_FAILED: String = "GUARD_FAILED"
        const val GATEWAY_REPO_NOT_PRIVATE: String = "REPO_NOT_PRIVATE"
        const val GATEWAY_ANCESTRY_FAIL: String = "ANCESTRY_FAIL"
        const val GATEWAY_DELTA_EMPTY: String = "DELTA_EMPTY"
        const val GATEWAY_MERGE_COMMITS: String = "MERGE_COMMITS"
        const val GATEWAY_DENIED_PATH: String = "DENIED_PATH"
        const val GATEWAY_SECRET_IN_DIFF: String = "SECRET_IN_DIFF"
        const val GATEWAY_SUBMISSION_IMMUTABLE: String = "SUBMISSION_IMMUTABLE"
        const val GATEWAY_UNKNOWN_REPO: String = "UNKNOWN_REPO"
        const val GATEWAY_RATE_BUDGET_LOW: String = "RATE_BUDGET_LOW"
        const val GATEWAY_RULESET_REJECTED: String = "RULESET_REJECTED"
        const val GATEWAY_RECONCILE_REQUIRED: String = "RECONCILE_REQUIRED"
        const val GATEWAY_NETWORK: String = "NETWORK"
        const val GATEWAY_CONFIG_INVALID: String = "CONFIG_INVALID"
        const val GATEWAY_NOT_FOUND: String = "NOT_FOUND"
        const val GATEWAY_ENROLL_CHALLENGE_INVALID: String = "ENROLL_CHALLENGE_INVALID"
        const val GATEWAY_ENROLL_ATTESTATION_INVALID: String = "ENROLL_ATTESTATION_INVALID"
        const val GATEWAY_AUTH_TIER_NOT_ALLOWED: String = "AUTH_TIER_NOT_ALLOWED"
        const val GATEWAY_NOT_IMPLEMENTED: String = "NOT_IMPLEMENTED"

        /**
         * The gateway's code for each state.
         *
         * The four V5–V8 rejections are in here and are distinct from each other,
         * which is the property the UI relies on to tell them apart.
         */
        private val GATEWAY_CODE_STATES: Map<String, GatewayFailureState> = mapOf(
            // Scope refused.
            GATEWAY_BRANCH_NOT_TASK to GatewayFailureState.SCOPE_REFUSED,
            GATEWAY_BRANCH_INVALID to GatewayFailureState.SCOPE_REFUSED,
            GATEWAY_BRANCH_MISMATCH to GatewayFailureState.SCOPE_REFUSED,
            GATEWAY_UNKNOWN_OP to GatewayFailureState.SCOPE_REFUSED,
            GATEWAY_NOT_ENROLLED to GatewayFailureState.SCOPE_REFUSED,
            GATEWAY_DEVICE_NOT_ENROLLED to GatewayFailureState.SCOPE_REFUSED,
            GATEWAY_AUTH_TIER_NOT_ALLOWED to GatewayFailureState.SCOPE_REFUSED,
            // The other three core states.
            GATEWAY_NONCE_REPLAYED to GatewayFailureState.REPLAYED,
            GATEWAY_HEAD_DRIFT to GatewayFailureState.HEAD_DRIFTED,
            GATEWAY_HEAD_MOVED to GatewayFailureState.HEAD_DRIFTED,
            GATEWAY_HALT_ACTIVE to GatewayFailureState.HALT_ACTIVE,
            // Authentication.
            GATEWAY_UNKNOWN_KID to GatewayFailureState.AUTHENTICATION,
            GATEWAY_BAD_SIGNATURE to GatewayFailureState.AUTHENTICATION,
            GATEWAY_OPERATOR_AUTH to GatewayFailureState.AUTHENTICATION,
            GATEWAY_CREDENTIAL_NOT_ACCEPTED to GatewayFailureState.AUTHENTICATION,
            // Format.
            GATEWAY_EXPIRED to GatewayFailureState.FORMAT,
            GATEWAY_CAPABILITY_EXPIRED to GatewayFailureState.FORMAT,
            GATEWAY_TTL_TOO_LONG to GatewayFailureState.FORMAT,
            GATEWAY_BAD_FIELD_COUNT to GatewayFailureState.FORMAT,
            GATEWAY_BAD_ENCODING to GatewayFailureState.FORMAT,
            GATEWAY_UNKNOWN_REPO to GatewayFailureState.FORMAT,
            GATEWAY_ENROLL_CHALLENGE_INVALID to GatewayFailureState.FORMAT,
            // Guard failures.
            GATEWAY_GUARD_FAILED to GatewayFailureState.GUARD_FAILED,
            GATEWAY_ENROLL_ATTESTATION_INVALID to GatewayFailureState.GUARD_FAILED,
            GATEWAY_REPO_NOT_PRIVATE to GatewayFailureState.GUARD_FAILED,
            GATEWAY_ANCESTRY_FAIL to GatewayFailureState.GUARD_FAILED,
            GATEWAY_DELTA_EMPTY to GatewayFailureState.GUARD_FAILED,
            GATEWAY_MERGE_COMMITS to GatewayFailureState.GUARD_FAILED,
            GATEWAY_DENIED_PATH to GatewayFailureState.GUARD_FAILED,
            GATEWAY_SECRET_IN_DIFF to GatewayFailureState.GUARD_FAILED,
            GATEWAY_RULESET_REJECTED to GatewayFailureState.GUARD_FAILED,
            // Conflict & lifecycle.
            GATEWAY_SUBMISSION_IMMUTABLE to GatewayFailureState.CONFLICT,
            GATEWAY_RECONCILE_REQUIRED to GatewayFailureState.CONFLICT,
            // Not found & rate limit.
            GATEWAY_BRANCH_MISSING to GatewayFailureState.NOT_FOUND,
            GATEWAY_NOT_FOUND to GatewayFailureState.NOT_FOUND,
            GATEWAY_RATE_BUDGET_LOW to GatewayFailureState.RATE_LIMITED,
            // Environment & gateway errors.
            GATEWAY_ENVIRONMENT_MISMATCH to GatewayFailureState.ENVIRONMENT_MISMATCH,
            GATEWAY_TOKEN_UNAVAILABLE to GatewayFailureState.GATEWAY_ERROR,
            GATEWAY_NETWORK to GatewayFailureState.GATEWAY_ERROR,
            GATEWAY_CONFIG_INVALID to GatewayFailureState.GATEWAY_ERROR,
            GATEWAY_NOT_IMPLEMENTED to GatewayFailureState.GATEWAY_ERROR,
        )

        /**
         * The gateway codes the console understands.
         */
        val GATEWAY_CODES: Set<String> = GATEWAY_CODE_STATES.keys

        private val json = Json { ignoreUnknownKeys = true }
    }
}
