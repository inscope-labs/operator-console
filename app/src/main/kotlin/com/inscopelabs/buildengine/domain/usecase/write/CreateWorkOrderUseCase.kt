package com.inscopelabs.buildengine.domain.usecase.write

import com.inscopelabs.buildengine.domain.model.WorkOrder

sealed interface CreateWorkOrderResult {
    /**
     * Work-order creation is outside the gateway contract and deferred to engine tooling
     * under BD-1.3 Option B (PR3-027). Nothing is minted or sent.
     */
    data class UnsupportedByContract(val reason: String) : CreateWorkOrderResult
}

/**
 * Work-order creation (BD-1.3 Option B, PR3-027, ADR 0014 D5).
 *
 * The gateway contract and frozen schemas define no §3 request or response and no schema for
 * `POST /plans/{id}/order`, so I-10 forbids the console implementing it. This use case removes
 * all scaffold placeholders and fails closed without minting or inventing wire calls.
 *
 * **This use case is deliberately not in the Hilt graph.** It carries no dependencies — no
 * repository, no capability minter, no network client — so it cannot mint a capability, cannot
 * write to the journal and cannot reach the gateway even if something were to call it. It is
 * also not provided by `UseCaseModule`, so no ViewModel, screen or repository can inject it.
 * That is what "removed from any operational path" means here: the deferral is recorded and
 * shown, and there is no route from the UI to a work-order creation call.
 *
 * The refusal reason comes from [WorkOrder.DEFERRED_REASON] so that the domain and the
 * operator-facing text cannot drift apart.
 */
class CreateWorkOrderUseCase {

    suspend operator fun invoke(plan: String, taskIds: List<String>): CreateWorkOrderResult =
        CreateWorkOrderResult.UnsupportedByContract(WorkOrder.DEFERRED_REASON)
}
