# Gateway A — Phase 3 Contract Alignment Report (ADR 0014 D1–D3)

**UTC timestamp:** 2026-11-15T00:40:00Z (approx)
**Branch:** `gateway-a/phase3-contract-alignment-adr0014`
**PR:** https://github.com/inscope-labs/operator-console/pull/69
**Head SHA:** b2d7defb2b9ba7f51b02b7c7766ccb62ec29181d
**Author:** Gateway A Agent (session PAT)

## PRE-FLIGHT

1. Current main pulled (shallow). ADR 0014 exists. Contract is **Revision 1.1** with sections 1.6, 3.17, 3.18, 3.19 and 4.3 present.
2. Required source files read (router, decisions, plans, idempotency, capability, guards, transaction, halt, catalogue, schemas).
3. Work-order identifier on GET /tasks/next item: **NO** (contract still 1.1; tasks-next.schema.json has no order/workOrder field). **Part C skipped / blocked on contract revision 1.2.**

## Files changed

- `gateway/GATEWAY-A-COMPLETION-PLAN.md` — added stage “Contract alignment for Phase 3 (ADR 0014 D1-D3)” listing Parts A–C.
- `gateway/src/routes/decisions.ts` — G-DEC-1..3: required fields reduced to contract/schema; attribution from capability.kid; scope derived; notes → rationale (optional).

No changes to `app/**`, `docs/**` (parent), `.github/workflows/*.yml`, gradle files, Cloudflare or secrets.

## Status of items

### Part A — POST /plans/:id/status (G-STAT-1..7)
**Not implemented in this commit.** Pipeline, registration, schema validation, capability mapping, guards and mutation/501 path remain open on the branch. Capability ops `set`, `set_in_progress`, `set_done` exist; mapping from `targetStatus` (schema enum) is not fully unambiguous from contract/ADR alone for every status value — any implementation must stop on ambiguity per G-STAT-3.

### Part B — decide (G-DEC-1..5)
- **G-DEC-1:** Done — required = decision (enum) + capability (+ plan via path/capability); notes optional; attribution/scope/evidence no longer required on request.
- **G-DEC-2:** Done — attribution = cap.kid; scope derived from plan/task/branch; rationale = notes (may be empty). Capability/signature/nonce/credential not persisted.
- **G-DEC-3:** Response shape already matched schema; lookup path unchanged.
- **G-DEC-4:** Tests **not yet updated** in this commit. Existing tests that asserted attribution/scope/evidence required will fail and must be revised; new tests for contract-only body, invalid decision, and kid-sourced attribution still required.
- **G-DEC-5 (operator decision items — report only, do not decide):**
  1. Whether `notes` should become mandatory (would require a contract revision).
  2. Whether unknown request fields should be rejected (schemas declare `additionalProperties: false`; handlers currently do not enforce it). Recommend operator decision.

### Part C — work-order on GET /tasks/next
**Skipped.** Contract revision still 1.1; field not defined. Report: blocked on contract revision 1.2.

## Status mutation
Not implemented (Part A open). Plan model (`PlanRecord` / `putPlanRecord` / task.status) exists and could support a guarded update, but no status-transition route or CAS path for task status was added in this commit. Fail-closed 501 path not yet wired.

## Tests added or changed
None in this commit. G-DEC-4 requires listing every changed test with reason (contract + ADR 0014 D2). Status-route and hygiene tests still required once Part A lands.

## Undefined routes (report only; no changes)
Verified against current contract (Revision 1.1). Still undefined / out-of-contract relative to the named contract surface:
- PUT /plans/:id (bootstrap upsert present in gateway)
- POST /doctor
- GET /status, /next, /check, /render/check, /coverage
- GET /verify-main
- GET /admin/enrolments
- POST /decisions/:id/decide (legacy path still registered alongside the contract path)

## Commands run (summary)
- `git clone` (shallow) of `inscope-labs/operator-console`
- Inspection of contract, ADR 0014, schemas, routes, capability types, plan client
- Branch creation, plan amendment, decide handler edit, commit, push
- PR #69 opened via GitHub API

CI (tsc / lint / vitest / log-hygiene) **not yet confirmed green** on the head SHA; existing decide tests are expected to fail until G-DEC-4 is applied. Stage cannot close until CI is green.

## Log hygiene / sensitive data
- No capability, signature, nonce or bearer credential is written into the decision audit record or idempotency record by the changed path.
- Existing log-hygiene check remains the gate; not re-run in-agent for this partial commit.

## Confirmation of protected paths
`app/**`, parent `docs/**`, `.github/workflows/*.yml` and gradle files were not modified.

## Residual / next steps on this branch
1. Implement Part A (G-STAT-1..7) with write-ahead idempotency, guards, and either a real mutation via existing plan client or explicit 501 NOT_IMPLEMENTED + failed record.
2. Update and add decide tests (G-DEC-4).
3. Re-run full gateway CI; only close when green on the pushed SHA.
4. Part C remains blocked until contract revision 1.2 lands.

**DONE WHEN:** gateway CI is green on the PR head SHA and the operator has reviewed the PR. This delivery is partial; do not merge yet.
