# Agent Report: Stage 1.3 — Contract schemas & error catalogue freeze

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 1, Stage 1.3
- **Timestamp (UTC)**: 2026-11-07T02-29-35Z
- **Slug**: stage-1-3-contract-schemas-catalogue-freeze
- **Branch**: `stage/1-3-contract-schemas-catalogue-freeze`

---

## 1. What this stage changed

### Error catalogue freeze
- Expanded `gateway/src/errors/catalogue.ts` with complete `ERROR_MESSAGE` for every code.
- Added contract-required codes not previously present: `BAD_FIELD_COUNT`, `BAD_ENCODING`, `ENVIRONMENT_MISMATCH`.
- Added `ALL_ERROR_CODES` export for freeze tests.
- Rebuilt `gateway/docs/error-catalogue.md` so it is identical to the TypeScript union.
- Documented parent-contract aliases (`HEAD_DRIFT` → `HEAD_MOVED`, etc.).
- Mapped capability `MALFORMED` verify result to `BAD_ENCODING` (was `NETWORK`).

### JSON Schemas
Created `gateway/docs/schemas/` covering:
health, plans, tasks (publish/upload/reconcile), branches, enrol, halt/resume, sync/doctor, decide, and the shared error body.

### ADR
- `gateway/docs/adr/0003-error-catalogue-and-schema-freeze.md`

### Tests
- `gateway/tests/unit/errors/catalogue-freeze.test.ts` asserts catalogue completeness and no 2xx statuses.

## 2. Exit criteria

| Criterion | Status |
|---|---|
| Schemas exist for health, plans, tasks, branches, enrol, publish, upload, reconcile, halt, resume, sync, doctor and decide | ✅ |
| Catalogue and TypeScript union are identical | ✅ |
| Conformance vectors can reference the schemas | ✅ (`gateway/docs/schemas/`) |

## 3. Residual contract coordination notes

Parent `docs/gateway-contract.md` still documents environment as `test|production` only
(Stage 1.2 added `staging` in the gateway) and uses body shape `{ code, message }`.
Gateway A freezes on `{ error, message, request_id }`. Aligning the parent document
is coordination work outside this stage's path list; aliases are recorded in the
catalogue.

## 4. Statement of completion

Stage 1.3 is complete pending CI green and operator review of the PR.
