package com.inscopelabs.buildengine.domain.model

/**
 * Read representation of a scoped work-order identifier referenced by capabilities.
 * Work-order creation is deferred to engine tooling under BD-1.3 Option B (PR3-027).
 */
data class WorkOrder(
    val id: String,
    val planId: String,
    val taskIds: List<String>,
) {
    companion object {

        /**
         * The canonical statement of the BD-1.3 Option B deferral (PR3-027, ADR 0014 D5).
         *
         * One source of truth: the use case's refusal reason and the operator-facing text
         * both come from here, so the console cannot describe the deferral two different
         * ways.
         *
         * The statement is accurate against gateway contract revision 1.2 (ADR 0015):
         * - §2 lists `POST /plans/{id}/order` in the cbridge command mapping, but the
         *   contract defines **no §3 request or response and no schema** for it, so I-10
         *   forbids the console implementing it.
         * - §3.6 states that work orders are created by engine tooling and that the
         *   gateway reports the identifier it holds for a task; the console copies that
         *   value unchanged and never derives or invents one.
         */
        const val DEFERRED_REASON: String =
            "Work-order creation is deferred to engine tooling under BD-1.3 Option B (PR3-027): " +
                "the gateway contract lists POST /plans/{id}/order in section 2 but defines no " +
                "section 3 request or response and no schema for it, so the console cannot " +
                "implement it (I-10). Work orders are created by engine tooling and the gateway " +
                "reports the identifier it holds for a task (contract section 3.6, revision 1.2); " +
                "the console copies that value and never derives or invents one."

        /** The contract clause that makes the deferral authoritative. */
        const val CONTRACT_REFERENCE: String = "docs/gateway-contract.md §3.6 (revision 1.2)"

        /** The operator decision that confirms BD-1.3 Option B. */
        const val DECISION_REFERENCE: String = "ADR 0014 D5"
    }
}
