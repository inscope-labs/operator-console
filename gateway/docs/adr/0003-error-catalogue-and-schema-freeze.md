# ADR 0003 — Error Catalogue and Contract Schema Freeze

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-07 |
| **Decision IDs** | GATEWAY-A-COMPLETION-PLAN Stage 1.3, RELEASE-PLAN T1-201, T1-202, BD-1.3, BD-1.4, BD-1.5, BD-1.7, R-06 |
| **Related** | `docs/gateway-contract.md`, `gateway/docs/error-catalogue.md`, `gateway/src/errors/catalogue.ts`, ADR 0002 |

---

## Context

Prior to this decision, several subtle drifts existed between the gateway implementation, the wire specification, and the Android client:
1. **Error response property naming**: The contract (`docs/gateway-contract.md §4`) and the Android client (`GatewayErrorDto`) specified that errors carry a `code` string (`{"code": "...", "message": "..."}`). The gateway implementation in `catalogue.ts` and `respond.ts` returned `error` (`{"error": "...", "message": "...", "request_id": "..."}`), causing the Android client's parser to observe an empty `code`.
2. **Operation naming & enumeration**: The wire specification and test fixtures used uppercase strings (`PUBLISH`, `UPLOAD`, `RECONCILE`, `DECIDE`, etc.), while the TypeScript codec checked against a lowercase closed set and lacked `new_plan`.
3. **Guard numbering**: Contract fixtures used legacy names (`G1_EMPTY_DIFF`), whereas the spec and codebase use G1–G15 (`G1` private, `G6` delta empty, `G8` denied path, `G13` exact head, `G14` halt).
4. **Machine-readable schemas**: There were no authoritative JSON Schemas defined for validating request and response payloads across components.

## Decision

1. **Reconcile error response shape with `code` authoritative**:
   - `GatewayErrorBody` specifies `code` as the primary, authoritative machine-readable code field.
   - `error` is mirrored alongside `code` in the JSON response (`{ code, error, message, request_id }`) for backwards compatibility with any existing tests asserting `body.error`.
   - Android's `GatewayErrorDto` and `ErrorMapper` support both `code` and the `error` alias through an `effectiveCode` fallback.
2. **Freeze public error catalogue**:
   - The TypeScript union `ErrorCode` in `src/errors/catalogue.ts`, the documentation in `gateway/docs/error-catalogue.md`, and the contract table in `docs/gateway-contract.md §4` are synchronized and frozen.
   - All codes used by upcoming phases (including `HEAD_MOVED`/`HEAD_DRIFT`, `DEVICE_NOT_ENROLLED`/`NOT_ENROLLED`, `HALT_ACTIVE` HTTP 423, `NOT_IMPLEMENTED` HTTP 501, guard codes G1–G15) are formally registered.
3. **Case-insensitive wire operation parsing**:
   - The gateway's `isOperationType` and `decodeCapability` normalize operation tokens case-insensitively while preserving canonical lowercase `OperationType` internally.
   - Support for all 13 operations is formalized (`publish`, `upload`, `reconcile`, `halt`, `resume`, `decide`, `order`, `set`, `new`, `new_plan`, `sync`, `doctor`, `set_in_progress`, `set_done`).
4. **Spec G1–G15 guard identity**:
   - Contract §5 fixture branches are updated to reference the authoritative spec numbers (G1 private, G3 shape, G6 delta, G7 merge commits, G8 denied path, G11 secrets, G13 exact head, G14 halt, G15 submission immutability).
5. **Publish formal JSON Schemas**:
   - 22 draft 2020-12 JSON Schemas are published under `docs/schemas/` and mirrored under `gateway/docs/schemas/` covering every request, response, and error payload in the contract.

## Consequences

- The Android application cleanly deserializes error responses and maps them into exact `GatewayFailureState` UI states.
- The TypeScript gateway accepts standard test vectors and Android client capabilities without casing discrepancies.
- Gateway CI and Android integration can perform automated schema conformance verification.
