# ADR 0014 — Phase 3 Operator Decisions

| | |
|---|---|
| **Status** | Accepted (takes effect when this PR is merged) |
| **Date** | 2026-11-14 |
| **Decision IDs** | D1 to D5 (this ADR); BD-1.3, BD-3.1 (plan decisions confirmed here) |
| **Related** | `docs/release/PHASE-3-REMEDIATION-REGISTER.md` (revision 1.2), ADR 0008, ADR 0012, ADR 0013, `docs/gateway-contract.md` |

---

## Context
Stages 3.3 to 3.5 surfaced contract and acceptance questions that blocked the Phase 3 remediation register:
the publish capability needs a work-order identifier the console cannot obtain; the `decide` schema cannot carry
every field Stage 3.4 asked for; the gateway lacks the status-transition route the client calls; one Stage 3.3
acceptance test cannot occur with the current confirmation design. The operator (John Wendell) reviewed Claude's
proposed defaults on 2026-11-14 and approved them for recording.

## Decisions

**D1 — Work-order identifier for publish.** The gateway returns the order identifier on the task read
(`GET /tasks/next`, contract §3.6) through a contract revision, and the console uses that value. The console never
invents, derives or hard-codes an order. Until the revision is merged and the gateway serves the field,
`PublishOrderResolver` stays fail-closed and publish cannot run end to end. The field's exact name and shape are
defined by the contract revision, not by this ADR.

**D2 — `decide` fields: documented reduction.** The console sends exactly what `decide.schema.json` defines:
`decision` and `notes`, plus the scope fields already in the schema. The operator's rationale travels in `notes`.
Attribution is the enrolled device key identifier recorded in the gateway's audit record. A separate evidence
field is not sent; evidence the operator wants recorded goes in `notes`. No schema change is required. The
reduction is recorded in `docs/STATUS.md` and the Phase 3 evidence dossier.

**D3 — Status-transition route.** The gateway agent adds `POST /plans/{id}/status` as defined in contract §3.17
(with the idempotency key and write-ahead record of ADR 0013). At the time of writing, the gateway registers only
the `GET` for that path, so the client's `POST` has no server. Integration evidence for PR3-026 waits for this.

**D4 — Capability lifetime acceptance.** The capability lifetime starts at approval by design. The Stage 3.3
acceptance case "TTL expiry while the confirmation prompt is open" is removed from the Gate 3 requirements,
because the confirmation use case reads approval time and checks expiry back to back and cannot produce it. It is
replaced by the testable criterion that a minted capability expires exactly the configured lifetime after approval.
`RELEASE-PLAN.md` T3-303 is revised by a separate operator-approved change.

**D5 — Plan decisions confirmed.**
- BD-1.3 **option B**: add `decide` and guarded status transitions only; work orders stay with engine tooling and
  the console shows the documented alternative. `CreateWorkOrderUseCase` is not an operational path.
- BD-3.1 **option (a)**: both confirmation tiers are accepted in staging and production; the gateway records the
  tier; `decide` and `resume` require `hardware_confirm` when the device can provide it.

## Not decided here (still open, operator action needed)
- Whether the register-added `[REG+]` items (PR3-040, PR3-041, PR3-043 instrumented jobs, the AES-GCM associated
  data requirement in PR3-010, and the scoring model) become Gate 3 requirements.
- Whether the edit to `app/proguard-rules.pro` in commit `7fdd304` is accepted after Claude's review, or re-landed.
- Which controlled gateway environment (deployed `test` Worker or local harness) PR3-042 runs against.

## Follow-ups this ADR creates (none is done by this PR)
1. A docs-only contract revision 1.2 defining the order identifier on the task read (D1).
2. A task for the gateway agent: the order field on `GET /tasks/next` and the status route (D1, D3).
3. A `RELEASE-PLAN.md` revision: T3-303 wording (D4) and a pointer to the register.
4. Register tasks PR3-003, PR3-021, PR3-025, PR3-026 and PR3-027 proceed against these decisions.
