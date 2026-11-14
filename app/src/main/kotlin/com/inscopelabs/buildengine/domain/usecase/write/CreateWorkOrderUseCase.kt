package com.inscopelabs.buildengine.domain.usecase.write

sealed interface CreateWorkOrderResult {
    /**
     * Work-order creation is outside the gateway contract and deferred to engine tooling
     * under BD-1.3 Option B (PR3-027). Nothing is minted or sent.
     */
    data class UnsupportedByContract(val reason: String) : CreateWorkOrderResult
}

/**
 * Work-order creation (BD-1.3 Option B, PR3-027).
 *
 * The gateway contract and frozen schemas define no `/orders` or work-order creation endpoint, so
 * work-order creation stays with engine tooling. This use case removes all scaffold placeholders
 * and fails closed without minting or inventing wire calls.
 */
class CreateWorkOrderUseCase {

    suspend operator fun invoke(plan: String, taskIds: List<String>): CreateWorkOrderResult =
        CreateWorkOrderResult.UnsupportedByContract(DEFERRAL_REASON)

    companion object {
        const val DEFERRAL_REASON: String =
            "Work-order creation is deferred under BD-1.3 Option B (PR3-027): no /orders endpoint " +
                "or order schema exists in the gateway contract; create work orders in engine tooling."
    }
}
