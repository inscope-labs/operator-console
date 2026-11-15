# ADR 0015 — Contract Revision 1.2

| | |
|---|---|
| **Status** | Proposed by Claude; takes effect when the operator merges this PR |
| **Date** | 2026-11-15 |
| **Decision IDs** | Follows ADR 0014 D1, D2, D3 |
| **Related** | `docs/gateway-contract.md` revision 1.2, ADR 0013, ADR 0014, register PR3-003 |

---

## Context
ADR 0014 decided that the gateway returns the work-order identifier on the task read (D1), that `decide` is a documented
reduction (D2) and that the gateway adds `POST /plans/{id}/status` (D3). It left the wire details to a contract
revision. The first Gateway A attempt at the status route also stopped on three ambiguities: the schema status
vocabulary disagrees with the gateway's internal names, the contract never mapped capability ops to statuses, and it
never said what the operation mutates.

## Decisions (revision 1.2)
- **R1 — Work-order field.** `GET /tasks/next` items carry a required, non-empty string `order`. The console copies it
  unchanged into the capability `order` field and never derives it. The gateway reports the value it holds (work orders
  stay with engine tooling, BD-1.3 option B) and does not list a task that has none.
- **R2 — Wire status vocabulary.** The schema vocabulary is authoritative (`completed`, not `done`); implementations map
  internal names at every route boundary (contract section 1.7).
- **R3 — Status transitions.** Only `in_progress` (op `SET_IN_PROGRESS`) and `completed` (op `SET_DONE`) are supported. The
  other schema values and op `SET` are reserved and refused with `BAD_ENCODING`. The operation updates the gateway's
  recorded task status and does not commit to the consumer repository. Check order, error codes and the concurrent-change
  refusal (`RECONCILE_REQUIRED`) are in contract section 3.17.
- **R4 — Decide derivation.** Attribution is the enrolled key identifier of the verified capability, `notes` is the optional
  rationale, scope is derived; there are no attribution, scope or evidence request fields (contract section 3.18).

## Consequences
- `tasks-next.schema.json` gains a required field; the gateway and the console must adopt it (gateway Part C; console
  `TaskSummaryDto` and `PublishOrderResolver`). Until then the Android conformance ratchet carries the baseline entry
  `response_missing_required:tasks-next:order`.
- No new error code is introduced. Unknown plan or task reuses `BAD_ENCODING`; a concurrent plan change reuses
  `RECONCILE_REQUIRED`.

## Still open (operator decisions, not made here)
- Whether a dedicated not-found error code is wanted (it would be a further contract revision touching the gateway
  catalogue freeze test and the console `ErrorMapper`).
- Whether `notes` on `decide` should become mandatory (the schema says optional).
- Whether unknown request fields should be rejected (the schemas say `additionalProperties: false`; the handlers do not
  enforce it).
- Whether `draft`, `ready`, `halted`, `archived` and `SET` should ever be supported.
