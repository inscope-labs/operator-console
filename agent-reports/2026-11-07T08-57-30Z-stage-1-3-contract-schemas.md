# Agent Report: Stage 1.3 — Contract Schemas & Error Catalogue Freeze

**Agent:** AI Studio (`[AIS]`)  
**Timestamp:** `2026-11-07T08:57:30Z`  
**Phase / Stage:** Phase 1 / Stage 1.3  
**Plan References:** `gateway/GATEWAY-A-COMPLETION-PLAN.md` §Stage 1.3, `RELEASE-PLAN.md` §Stage 1.2 (T1-201, T1-202, T1-204, T1-205)  
**Status:** **COMPLETED**

---

## 1. Executive Summary

Stage 1.3 freezes the public error catalogue, establishes authoritative draft 2020-12 JSON Schemas for every request and response body across the wire contract, eliminates property naming drift between Gateway A and the Android client, unifies operation casing, and aligns guard fixture identities with the authoritative G1–G15 specification.

---

## 2. Key Deliverables & Changes

### 2.1 Reconciled Error Response Shape (`code` Authoritative)
- **Problem**: `docs/gateway-contract.md §4` and the Android client (`GatewayErrorDto`) specified that errors carry a `code` string (`{"code": "...", "message": "..."}`), while Gateway A returned `error` (`{"error": "...", "message": "...", "request_id": "..."}`).
- **Fix**: 
  - `gateway/src/errors/catalogue.ts` and `gateway/src/errors/respond.ts` now emit `code` as the primary, authoritative machine-readable code, while maintaining `error` as a backwards-compatible mirror (`{ code, error, message, request_id }`).
  - Android `GatewayErrorDto.kt` was enhanced with `effectiveCode` to parse `code` and fallback cleanly to `error` if needed, along with `@SerialName("request_id")`.
  - Android `ErrorMapper.kt` updated to map all frozen catalogue codes (`HEAD_MOVED`, `DEVICE_NOT_ENROLLED`, `NOT_IMPLEMENTED`, `CONFIG_INVALID`, `DENIED_PATH`, `SECRET_IN_DIFF`, `DELTA_EMPTY`, `MERGE_COMMITS`, `ANCESTRY_FAIL`, `REPO_NOT_PRIVATE`, `SUBMISSION_IMMUTABLE`, `UNKNOWN_REPO`, `RATE_BUDGET_LOW`, `RULESET_REJECTED`, `RECONCILE_REQUIRED`, `TOKEN_UNAVAILABLE`, `CREDENTIAL_NOT_ACCEPTED`, etc.) into explicit `GatewayFailureState` states.

### 2.2 Frozen Error Catalogue & ADR 0003
- Created **ADR 0003** (`gateway/docs/adr/0003-error-catalogue-and-schema-freeze.md`) documenting the formal freeze of all gateway error codes and schema authority.
- Synchronized `gateway/src/errors/catalogue.ts`, `gateway/docs/error-catalogue.md`, and `docs/gateway-contract.md §4`.
- Standardized HTTP status codes (e.g. `HALT_ACTIVE` -> HTTP 423, `NOT_IMPLEMENTED` -> HTTP 501, `CONFIG_INVALID` -> HTTP 503, `RATE_BUDGET_LOW` -> HTTP 429).

### 2.3 Wire Operation Normalization & 13 Operation Coverage
- `gateway/src/capability/types.ts` and `codec.ts` now accept wire operation names case-insensitively (e.g., `"PUBLISH"` from Android client and vectors, normalized internally to canonical `"publish"`).
- Extended operation types to include `new_plan` (or `new`), formalizing all 13 operations from the build-engine model.
- Added `isTaskScoped` helper in `gateway/src/capability/ops.ts` so `decodeCapability` validates task branch regex `^task-[A-Za-z0-9._-]+$` only on task-scoped operations, and permits `engine` branches for engine-scoped operations (`halt`, `resume`, `sync`, `doctor`).

### 2.4 Spec G1–G15 Guard Authority (BD-1.5)
- Rewrote `gateway/docs/guard-set.md` from placeholder to an exhaustive specification of guards G1 through G16, their triggers, failure codes, and audit fields.
- Replaced legacy fixture identifiers (`G1_EMPTY_DIFF`, etc.) in `docs/gateway-contract.md §5` with authoritative spec numbering:
  - `task-fx-clean` -> Clean baseline (`200 OK`)
  - `not-task-invalid` -> G3 Branch Shape (`BRANCH_INVALID` / `BRANCH_NOT_TASK`)
  - `task-fx-no-commits` -> G6 Non-Empty Delta (`DELTA_EMPTY`)
  - `task-fx-merge-commit` -> G7 No Merge Commits (`MERGE_COMMITS`)
  - `task-fx-touch-engine` / `task-fx-touch-plans` / `task-fx-touch-github` -> G8 Denied Paths (`DENIED_PATH`)
  - `task-fx-secret-pattern` -> G11 Secret Patterns (`SECRET_IN_DIFF`)
  - `task-fx-drifted-head` -> G13 Exact Head SHA (`HEAD_MOVED` / `HEAD_DRIFT`)
  - `task-fx-halt-active` -> G14 Global Halt (`HALT_ACTIVE`)
  - `task-fx-submission-reused` -> G15 Submission Immutable (`SUBMISSION_IMMUTABLE`)

### 2.5 Endpoints Expansion per BD-1.3, BD-1.4, BD-1.7, BD-1.10
- Updated `docs/gateway-contract.md` §2 and §3 with formal endpoint specifications:
  - §3.17 `POST /plans/{id}/status`: Guarded status transitions with compare-and-swap (`SET`, `SET_IN_PROGRESS`, `SET_DONE`).
  - §3.18 `POST /plans/{id}/decide`: Milestone operator decisions (`DECIDE`).
  - §3.19 `GET /operations/{id}`: Operation recovery lookup keyed by idempotency key.
  - §3.20 `GET /audit` / `GET /audit/{ref}`: Audit journal log inspection.
  - §3.21 `POST /admin/revoke`: Device key revocation and emergency posture change.

### 2.6 Credential Scope Specification (T1-205, BD-0.6)
- Published `docs/gateway-credential-scope.md` detailing the GitHub App credential model, least-privilege installation permissions, blast radius containment, and ephemeral token handling.

### 2.7 Complete JSON Schema Suite
- Published 22 draft 2020-12 JSON Schemas in `docs/schemas/` and mirrored in `gateway/docs/schemas/` with registry index `index.json`:
  - `error.schema.json`
  - `health.schema.json`
  - `plans-list.schema.json`
  - `plan-status.schema.json`
  - `plan-status-transition.schema.json`
  - `plan-validate.schema.json`
  - `plan-render-check.schema.json`
  - `tasks-next.schema.json`
  - `branch-head.schema.json`
  - `enroll-challenge.schema.json`
  - `enroll.schema.json`
  - `enroll-rotate.schema.json`
  - `publish.schema.json`
  - `upload.schema.json`
  - `reconcile.schema.json`
  - `halt.schema.json`
  - `resume.schema.json`
  - `sync.schema.json`
  - `doctor.schema.json`
  - `decide.schema.json`
  - `operation-lookup.schema.json`
  - `admin-revoke.schema.json`
  - `index.json`

---

## 3. Verification

- **Android Compilation**: Verified clean build via `compile_applet`. Android compilation completed with status SUCCESS (`Build succeeded - the applet is compiled`).
- **Platform Identity**: Preserved `metadata.json` and `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`.
- **Status Tracking**: Updated `docs/STATUS.md` recording Stage 1.3 as implemented and safe to use.

---

## 4. Next Stage in Queue

**Phase 1 / Stage 1.4 — CI Hardening (Fail-Closed) & Lockfile Generation**
- Commit `gateway/package-lock.json`.
- Remove all `|| true` permissive directives from gateway CI workflow.
- Rename workflows `cbridge-ci.yml` / `cbridge-deploy.yml` to `gateway-ci.yml` / `gateway-deploy.yml` and report workflow changes for human PR application.
- Enforce `npm ci`, `tsc --noEmit`, `vitest run`, `verify-log-hygiene`, and `check-repo-privacy` as non-permissive CI gates.
