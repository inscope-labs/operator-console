package com.inscopelabs.buildengine.domain.model

/**
 * The outcome of a consequential operation (T3-201).
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 * TODO(Stage 3.2 / T3-201): define each outcome's payload from docs/gateway-contract.md; a sent request is never reported as a success (G-08).
 *
 * Pre-execution scaffold (docs/PRE-EXECUTION-ONBOARDING.md). Not wired into navigation, DI or any caller.
 */
sealed interface OperationOutcome {

    data object Succeeded : OperationOutcome

    data object Refused : OperationOutcome

    data object Conflict : OperationOutcome

    data object Timeout : OperationOutcome

    data object UncertainCompletion : OperationOutcome

    data object RecoveryRequired : OperationOutcome
}
