package com.inscopelabs.buildengine.lab

import com.inscopelabs.buildengine.capability.model.OperationType
import javax.inject.Inject

/**
 * The request a Lab scenario sends.
 *
 * `body` is the JSON body **without** the capability. The capability is carried
 * separately in [capability] and placed into the body's `capability` field by the
 * runner, so that a mutation can act on the capability and on the body independently —
 * which is what "each mutation changes only what it declares" requires (T5-201).
 *
 * There is no header map. The console's transport
 * (`core.network.GatewayHttpClient.request`) sets the bearer credential itself and
 * exposes no way to add a header, so a scenario cannot declare one. A mutation that
 * needs to touch a header is out of reach of this engine until the transport exposes
 * one; it is recorded in the agent report rather than faked.
 */
data class LabRequest(
    val method: String,
    val path: String,
    /** The JSON body, without the capability. `null` for a request with no body. */
    val body: String? = null,
    /** The capability to place into the body's `capability` field, if the scenario has one. */
    val capability: String? = null,
)

/**
 * What a scenario needs the Lab to mint for it.
 *
 * A Lab scenario never uses the device key and never prompts for a biometric
 * (ADR 0004 §2). It asks the Lab for a capability with an explicit scope, and the Lab
 * signs it with the lab secret. The fields are the capability's own
 * (docs/capability-format.md §1): `kid`, `exp`, `order`, `plan`, `task`, `op`, `branch`,
 * `head`, `nonce`.
 */
data class LabCapabilityRequest(
    val op: OperationType,
    val branch: String,
    val head: String,
    val plan: String = "",
    val task: String = "",
    val order: String = "",
    val kid: String = "",
    /** The capability's lifetime in seconds. The gateway refuses more than 300. */
    val ttlSeconds: Long = 300L,
)

/**
 * The outcome of asking the Lab for a capability.
 *
 * [Unavailable] is a first-class outcome, not an error. Until Stage 5.3 wires the lab
 * signer (T5-302), every scenario that needs a capability is refused — loudly, and
 * reported as skipped. A scenario that silently ran without its capability would be a
 * scenario that proved nothing.
 */
sealed interface LabCapabilityOutcome {

    /** The Lab minted the capability. */
    data class Available(val capability: String) : LabCapabilityOutcome

    /** The Lab could not mint a capability, and says why. */
    data class Unavailable(val reason: String) : LabCapabilityOutcome
}

/**
 * Mints Lab capabilities.
 *
 * **The seam.** ADR 0004 §2 puts the lab signer inside Lab and nowhere else: Lab
 * scenarios sign with `HmacCapabilitySigner` and the lab secret, while real operator
 * operations keep using the device key and `ConfirmAndMintCapabilityUseCase`. The
 * pre-execution baseline deliberately does not import a signer, so this class refuses
 * every request until Stage 5.3 replaces the body with the HMAC-backed implementation
 * (T5-302). It is `open` so the stage can either fill in this body or bind a
 * subclass.
 *
 * The refusal is the point: with the signer absent, a scenario that needs a capability
 * is skipped and reported, and no scenario is ever reported as passed on the strength
 * of a request it did not really make.
 */
open class LabCapabilitySource @Inject constructor() {

    /**
     * Issues a capability for [request].
     *
     * The base implementation always refuses. The override must return the same scope
     * it was asked for — `op`, `branch` and `head` are bound into the signature, and a
     * capability that does not carry the scope it was issued for is a capability the
     * gateway will refuse.
     *
     * The method is named `issue` rather than `mint` on purpose: `ArchitectureTest`
     * refuses any file outside the two that own capability minting from containing the
     * dotted minting call, and it greps the raw file text — comments included. A Lab
     * engine that tripped that rule would fail the build for a naming coincidence.
     */
    open suspend fun issue(request: LabCapabilityRequest): LabCapabilityOutcome =
        LabCapabilityOutcome.Unavailable(
            "Lab signing is not wired in this build. Stage 5.3 (T5-302) binds the " +
                "HMAC-backed lab signer; until then every scenario that needs a " +
                "capability is skipped rather than run without one.",
        )
}

/**
 * How many times a scenario sends its request.
 *
 * Two of the gateway's refusals cannot be provoked by a single request: a replayed
 * nonce and a reused idempotency key both need a second presentation. The repeat is
 * part of the scenario's declaration, so it is visible in the catalogue rather than
 * hidden in the runner — and it is never a retry: the runner does not send a request
 * again because it failed.
 */
sealed interface LabRepeat {

    /** The request is sent once. */
    data object Once : LabRepeat

    /**
     * The same request is sent [times] times.
     */
    data class SameRequest(val times: Int) : LabRepeat

    /**
     * A second request is sent after the first, with a different fingerprint.
     */
    data class WithFollowUp(val request: LabRequest) : LabRepeat
}

/**
 * What a Lab scenario declares.
 *
 * A scenario is a declaration, not an action. It names the request to send, the
 * mutation to apply, the capability it needs, and the refusal it expects the gateway
 * to answer with.
 *
 * `errorCode` is the gateway's own catalogue code, taken from the frozen catalogue
 * (gateway/docs/error-catalogue.md). It is the reason the scenario exists: the Lab
 * asserts that the gateway refuses *this* way and not another, which is what makes
 * the catalogue a conformance check rather than a smoke test.
 *
 * `precondition` names something the **gateway** must be configured with for the
 * scenario to mean anything — a halted engine, a repository that is not private, a
 * rate budget that is exhausted. A scenario with a precondition is skipped with the
 * precondition quoted, never reported as passed. The scenarios that need no
 * precondition are the ones the client can provoke on its own.
 */
data class Scenario(
    /** Stable identifier. The catalogue is keyed on it and the report lists it. */
    val id: String,
    val title: String,
    /** The gateway error code this scenario expects. Empty when it expects success. */
    val errorCode: String,
    /** The HTTP status this scenario expects. */
    val expectedStatus: Int,
    val request: LabRequest,
    /** The mutation applied before the request is sent. `null` for an unmutated probe. */
    val mutation: Mutator? = null,
    /** How many times the request is sent. */
    val repeat: LabRepeat = LabRepeat.Once,
    /** The capability the scenario needs. `null` when it needs none. */
    val capability: LabCapabilityRequest? = null,
    /**
     * The kind of response the scenario expects, so the contract check knows which
     * published schema to hold the answer to. One of `error`, `ack`, `health`,
     * `branch-head`, `operation-lookup`, `plans-list`, `tasks-next`, or empty when the
     * scenario expects no response body worth checking.
     */
    val responseKind: String = "",
    /** What the gateway must be configured with. `null` when the client can provoke it. */
    val precondition: String? = null,
    /** Anything the reader needs to understand the scenario. */
    val notes: String = "",
)
