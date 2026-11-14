package com.inscopelabs.buildengine.domain.model

/**
 * The outcome of a consequential operation (docs/gateway-contract.md §4.3, T3-201).
 *
 * A sent request is never reported as [Succeeded] from dispatch alone (G-08): success
 * requires either an accepted gateway acknowledgement carrying non-blank `auditRef` and
 * `headSha`, or a `completed` lookup verified against the live branch head (T3-302).
 */
sealed interface OperationOutcome {

    /**
     * The operation succeeded and the gateway recorded [auditRef] and [headSha]
     * (verified against the live branch head on recovery lookup).
     */
    data class Succeeded(
        val auditRef: String,
        val headSha: String,
        val outcome: String? = null,
    ) : OperationOutcome {
        init {
            require(auditRef.isNotBlank()) { "Succeeded requires a non-blank auditRef" }
            require(headSha.isNotBlank()) { "Succeeded requires a non-blank headSha" }
        }
    }

    /**
     * Policy, guard, auth, or halt refusal carrying contract error [code].
     */
    data class Refused(
        val code: String,
        val auditRef: String? = null,
    ) : OperationOutcome {
        init {
            require(code.isNotBlank()) { "Refused requires a non-blank error code" }
        }
    }

    /**
     * Concurrency, replay, immutability, ancestry, reconcile, or idempotency-reuse conflict
     * carrying contract error [code].
     */
    data class Conflict(
        val code: String,
        val auditRef: String? = null,
    ) : OperationOutcome {
        init {
            require(code.isNotBlank()) { "Conflict requires a non-blank error code" }
        }
    }

    /**
     * Reserved strictly for a write send that received no response. Always followed by lookup.
     */
    data class Timeout(
        val idempotencyKey: String,
    ) : OperationOutcome

    /**
     * Completion is uncertain: post-send interruption, `pending` lookup, or `completed`
     * lookup whose recorded [headSha] no longer matches the live branch head.
     */
    data class UncertainCompletion(
        val idempotencyKey: String,
        val headSha: String? = null,
        val auditRef: String? = null,
    ) : OperationOutcome

    /**
     * Failed operation (`failed`), `404 OPERATION_NOT_FOUND`, unknown lookup status/code,
     * or journal encryption/decryption failure. Never auto-resent; operator must review.
     */
    data class RecoveryRequired(
        val idempotencyKey: String,
        val code: String? = null,
        val auditRef: String? = null,
    ) : OperationOutcome
}
