# Agent Report — Operation idempotency record and lookup (BD-1.7)

**UTC:** 2026-11-13T14:39:43Z  
**Branch:** `gateway/idempotency-lookup-bd17`  
**Head SHA (this run):** `969c694`  
**Base:** `main` @ `7fdd304`  
**Repo paths touched:** `gateway/**` only  

## PRE-FLIGHT

- Pulled/cloned current `main`. Confirmed:
  - `docs/gateway-contract.md` Revision 1.1, contains §1.6, §3.19, §4.3.
  - `docs/adr/0013-operation-idempotency-key-and-lookup.md` exists.
  - `docs/schemas/operation-lookup.schema.json` includes required `code` (for failed/refused) and completed invariants.
- Read plan, router, catalogue, audit/emit, adapter/transaction, halt/state, error-catalogue.md.
- Plan already contained the Amendment section for BD-1.7; left in place (no renumbering).

## Files changed in this run

| Path | Change |
|------|--------|
| `gateway/src/idempotency/store-client.ts` | Memory fallback now applies `PENDING_TIMEOUT_SECONDS` (15 min) the same way the Durable Object does; stale pending → `failed`/`NETWORK` and becomes replayable. |
| `gateway/tests/unit/idempotency/write-gate.test.ts` | Added cases: uppercase key, 31/33-char keys, NOT_IMPLEMENTED → failed, pending-timeout recovery. |

**Pre-existing on main (verified, not rewritten):** full G-IDEM-1..5 implementation under `gateway/src/idempotency/`, `gateway/src/durable-objects/operation-store.ts`, write routes (tasks/halt/decisions/plans), `GET /operations/:id`, catalogue codes, gateway docs, and substantial unit coverage.

## Task status

| Task | Status | Notes |
|------|--------|-------|
| G-IDEM-1 Request field | Complete | `isValidIdempotencyKey` + gate on every privileged write; 400 IDEMPOTENCY_KEY_INVALID. Not part of capability. |
| G-IDEM-2 Write-ahead record | Complete | Order: auth → key validation → begin → capability/guards → finish. Record key = (credentialHash, idempotencyKey). Fingerprint SHA-256 over operation + body fields excluding `capability` and `zipBase64`. Same key+fp → replay stored response (no re-exec, no nonce consume). Different fp → 409. Terminal states update record. Pending timeout 15 min → failed/NETWORK. Retention ≥7 days + DO alarm. |
| G-IDEM-3 Lookup route | Complete | GET /operations/{id}, Bearer only, schema shape, 400/404 as specified. Never executes. |
| G-IDEM-4 Errors | Complete | Catalogue + gateway/docs/error-catalogue.md match contract §4.1 descriptions and statuses. Freeze tests green. |
| G-IDEM-5 Fail closed / honest status | Complete | outcomeFromErrorCode maps NOT_IMPLEMENTED → failed; finishFromResponse never invents completed for error statuses. |
| G-IDEM-6 Tests | Complete (expanded) | See below. |

## Storage / atomicity design

- **Primary:** Durable Object `OperationStore` (`idFromName(\`cred:${credentialHash}\`)`). Single-writer atomicity provided by the DO runtime; concurrent same-key begins are serialized.
- **Unit tests:** process-local `Map` fallback in `store-client.ts` (reset per test). Now mirrors pending-timeout behaviour.
- **Pending timeout:** 15 minutes (`PENDING_TIMEOUT_SECONDS`). Stale pending is rewritten to `failed`/`NETWORK` on next begin with the same fingerprint (or observed via alarm path in DO).
- **Retention:** 7 days (`RETENTION_SECONDS`); DO alarm schedules purge of non-pending records older than retention; pending younger than retention is never deleted.

## Assumptions

- Implementation of the core feature set was already present on `main` when this assignment started; this run verified against contract/ADR/schemas, closed the memory-store parity gap, and expanded unit assertions required by G-IDEM-6.
- Empty conformance placeholders under `tests/conformance/` are out of scope (CI runs `test:unit` only).
- Plan status transition write path, if added later, must call the same gate (already documented in plan amendment).

## Tests and what they prove

- Key format: missing / uppercase / 31-char / 33-char → 400 IDEMPOTENCY_KEY_INVALID (gate + key unit tests).
- Write-ahead ordering: begin creates `pending` before any capability work would run.
- Same key + same fingerprint → recorded response returned, no re-execution.
- Same key + different fingerprint → 409 IDEMPOTENCY_KEY_REUSED.
- Terminal mapping: refused (e.g. HEAD_MOVED) carries code; NOT_IMPLEMENTED → failed.
- Lookup: 401 without Bearer, 400 malformed id, 404 unknown / other credential / still-pending, 200 completed with auditRef+headSha, refused with code.
- Fingerprint stability: capability and zipBase64 ignored; zipSha256 included.
- Pending timeout: record older than 15 min becomes failed/NETWORK and is replayable.
- Log hygiene: verify-log-hygiene scans 78 files, OK; no capability/signature/nonce/credential in records or logs.
- Catalogue freeze tests remain green.

## Commands run (summaries)

```
npm ci                          # success, 194 packages
npm run typecheck               # clean
npm run test:unit               # 40 files, 259 tests passed
npm run verify-log-hygiene      # OK (78 files)
npm run lint                    # deferred structural (typecheck is gate)
```

Nothing marked “unverified in-agent”; all gates executed locally against the pushed SHA.

## Confirmations

- No capability, signature, nonce, or raw bearer credential is stored in operation records or emitted in logs.
- `app/**`, `docs/**` (parent contract/schemas), and `.github/workflows/*.yml` were not modified.
- No Cloudflare deploy, no secrets written, no dependency additions.
- Branch pushed; PR creation form presented to operator for review (do not merge from this agent).

## DONE criteria

- All six G-IDEM tasks complete.
- Gateway unit CI green on the pushed SHA (typecheck + test:unit + hygiene).
- Operator review of the PR required to close the stage.

