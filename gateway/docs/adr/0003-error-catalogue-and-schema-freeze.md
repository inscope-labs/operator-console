# ADR 0003 — Error catalogue and schema freeze (Stage 1.3)

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-07 |
| **Decision IDs** | GATEWAY-A-COMPLETION-PLAN Stage 1.3, R-06, R-07 |
| **Related** | `gateway/docs/error-catalogue.md`, `gateway/src/errors/catalogue.ts`, `gateway/docs/schemas/`, ADR 0002 |

---

## Context

Stage 1.3 requires a frozen public error catalogue and machine-readable JSON
schemas for every request/response body defined in the operator gateway contract.
Before this stage the catalogue documentation was incomplete, several contract
error names differed from the TypeScript union, and no schemas existed for
conformance vectors to reference.

## Decision

1. **Runtime authority** is `gateway/src/errors/catalogue.ts`. Every public code,
   its HTTP status, and a default message live there.
2. **Documentation authority** is `gateway/docs/error-catalogue.md`, kept identical
   to the TypeScript union.
3. **Response body shape** is frozen as:
   ```json
   { "error": "<ErrorCode>", "message": "<string>", "request_id": "<string>" }
   ```
   Parent contract §4 historically used `{ "code", "message" }`. Gateway A does
   not change shape mid-flight; clients already consume `error` + `request_id`.
4. **Aliases** from parent contract names to frozen codes are documented in the
   catalogue (e.g. `HEAD_DRIFT` → `HEAD_MOVED`, `EXPIRED` → `CAPABILITY_EXPIRED`).
5. **JSON Schemas** under `gateway/docs/schemas/` cover health, plans, tasks,
   branches, enrol, publish/upload/reconcile, halt/resume, sync, doctor, and
   decide. Success bodies allow additional properties per contract §1.5.
6. **New codes** after this freeze require a simultaneous update of TypeScript,
   the markdown catalogue, and (where applicable) schemas. Changing the meaning
   of an existing code requires a new ADR.

## Consequences

- Conformance vectors and the Android `ErrorMapper` must use frozen names only.
- Stage 2.4 capability edge-case tests can rely on `BAD_FIELD_COUNT` and
  `BAD_ENCODING` being present.
- Lab environment safety can refuse with `ENVIRONMENT_MISMATCH`.
