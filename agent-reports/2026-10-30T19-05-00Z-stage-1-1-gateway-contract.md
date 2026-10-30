# Agent Report: Stage 1.1 — Gateway Contract

- **Stage**: Phase 1, Stage 1.1
- **Timestamp (UTC)**: 2026-10-30T19:05:00Z
- **Slug**: stage-1-1-gateway-contract

---

## Tasks Completed

- **T1-101 Contract skeleton**: Created `docs/gateway-contract.md` with protocol framing, auth headers (`Authorization: Bearer <operator_credential>`), capability payload placement in JSON body, UTF-8 JSON content-type specification, versioning, clock rules (300 s maximum TTL), and idempotency/replay handling.
- **T1-102 Endpoints specification**: Fully documented all required gateway endpoints:
  - `GET /health` (with `"environment": "test"|"production"`)
  - `GET /plans`, `GET /plans/{id}/status`, `POST /plans/{id}/validate`, `GET /plans/{id}/render-check`
  - `GET /tasks/next`, `GET /branches/{name}/head`
  - `POST /tasks/{id}/publish`, `POST /tasks/{id}/reconcile`, `POST /tasks/{id}/upload`
  - `POST /halt`, `POST /resume`, `POST /sync`, `GET /doctor`
  - `POST /enroll`, `POST /enroll/rotate`
  - Included direct mapping from legacy `cbridge` commands to gateway endpoints, explicitly marking `init`/`bootstrap` as out of scope.
- **T1-103 Error taxonomy**: Documented all 16 standardized error codes with corresponding HTTP status codes and validation stages:
  - `UNKNOWN_KID`, `BAD_SIGNATURE`, `EXPIRED`, `TTL_TOO_LONG`, `NONCE_REPLAYED`, `UNKNOWN_OP`, `BAD_FIELD_COUNT`, `BAD_ENCODING`, `BRANCH_NOT_TASK`, `BRANCH_MISMATCH`, `HEAD_DRIFT`, `HALT_ACTIVE`, `GUARD_FAILED`, `OPERATOR_AUTH`, `NOT_ENROLLED`, `ENVIRONMENT_MISMATCH`.
  - Defined the UI state mapping into the 4 required distinct UI states (*Scope Refused*, *Replayed*, *Head Drifted*, *Halt Active*).
- **T1-104 Fixture branches**: Documented table of `task-fx-*` branches for the test consumer repo targeting each guard rule (`clean`, `not-task-invalid`, `no-commits`, `merge-commit`, `touch-engine`, `touch-plans`, `touch-github`, `deny-text`, `secret-pattern`, `disallowed-author`, and `drifted-head`).

---

## Files Changed

- `docs/gateway-contract.md` (created)

---

## Tests Added

- None in this stage (Phase 1 Stage 1.1 is docs and specifications only per plan).

---

## Skipped Items and Rationale

- None skipped.

---

## Stop and Ask Items

- Ready to proceed to **Phase 1, Stage 1.2 — Decision records** (`docs/adr/0004-lab-mode.md`, `docs/adr/0005-signature-encoding.md`, `docs/adr/0006-mtls-deferred.md`).
