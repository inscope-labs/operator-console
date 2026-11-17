# ADR 0016 — Contract Revision 1.3: completion (`SET_DONE`) deferred

| | |
|---|---|
| **Status** | Proposed by Claude; takes effect when the operator merges this PR |
| **Date** | 2026-11-17 |
| **Decision IDs** | D-1 (decided), D-2 (decided) |
| **Related** | `docs/gateway-contract.md` revision 1.3 (§3.17), ADR 0014 D3, ADR 0015 R3, register PR3-026, Manual Pipeline Assessment v2 (Issue 1, Decisions 1, 4, 5) |

---

## Context
ADR 0015 R3 made both `SET_IN_PROGRESS` and `SET_DONE` supported on `POST /plans/{id}/status`. Neither is implemented by the
gateway yet. Marking a task done is where the assessment's open questions sit: there is no documented route from a Gateway A
upload to a Gateway C seal (Issue 1, Decision 1), Gateway A has no read-only credential to read the seal fact (Decision 4), and
the transition table itself is undecided (Decision 5). Contract §3.17 has no completion-evidence field, so the console's local
completion-evidence requirement was never sent on the wire.

## Decisions
- **D-1 — Completion deferred (Option A).** The operator chose, on 2026-11-17, that the console supports only `SET_IN_PROGRESS`
  for now. In revision 1.3 `completed` and capability op `SET_DONE` are reserved and refused with `400 BAD_ENCODING`, like `SET`.
  This is a deferral until Decisions 1 and 4 land; it does not decide them, and it does not decide whether `SET` or the other
  reserved statuses are ever supported.
- **D-2 — Integration environment: local harness.** The operator chose the recommended option on 2026-11-17: the integration
  evidence for PR3-026 is produced by a local harness (gateway conformance tests plus the console contract vectors; deterministic,
  no deployed state or credentials). The alternative, a run against the deployed `test` Worker, would prove Durable Object and
  Cloudflare behaviour end to end but needs credentials and shared state; it is not required to close PR3-026 and stays with the
  wider scope of register item PR3-042. This decision does not choose the environment for PR3-042 as a whole.

## Consequences
- The gateway implements `POST /plans/{id}/status` for `SET_IN_PROGRESS` only and refuses everything else as reserved.
- The console offers no “Complete” action. Its local completion-evidence requirement ends with the `DONE` path; the status-transition
  use case treats `DONE` as unsupported. A completion-evidence rule returns with the seal route decision.
- PR3-026 integration evidence comes from the local harness (D-2) and covers `SET_IN_PROGRESS` only.
- PR3-026 can close for `SET_IN_PROGRESS` only. Its acceptance line “Done requires the contract-defined evidence” is deferred with
  `SET_DONE`, and the register says so (Appendix D).
- The manual pipeline readiness register (`OC-MP-PRR-001`, LIF-02, LIF-07) stays consistent: the module does not call `SET_DONE`.
- The docs-only change touches `docs/gateway-contract.md` and the description text of `docs/schemas/plan-status-transition.schema.json`; the
  schema's enum is unchanged, so no vector or generated schema output changes.

## Still open (operator decisions, not made here)
- The controlled gateway environment for PR3-042 as a whole (D-2 covers PR3-026 evidence only).
- Assessment Decisions 1 (seal route), 4 (read-only seal credential) and 5 (transition table), which gate reinstating `SET_DONE`.
