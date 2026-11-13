# Agent Report: Operation idempotency record and lookup (BD-1.7)

- **Branch:** `stage/bd-1-7-operation-idempotency`
- **Scope:** `gateway/**` only (G-13)
- **Authority:** contract rev 1.1 §1.6, §3.19, §4.3; ADR 0013; operation-lookup schema

## Storage / atomicity design

- **Durable Object `OperationStore`**, one instance per **SHA-256(operator credential)** (`idFromName('cred:' + hash)`).
- Concurrent same-key requests are **serialized** by the DO single-writer runtime.
- Memory fallback map for unit tests when `OPERATION_STORE` is unbound.
- Record stores: id, credentialHash, operation, fingerprint, status, timestamp, optional branch/headSha/auditRef/code, optional responseStatus/responseBody for replay. **No** capability, signature, nonce, raw credential, or zipBase64.

## Pending-timeout design

- `PENDING_TIMEOUT_SECONDS = 15 minutes`: stale `pending` is marked `failed` with code `NETWORK` on begin/purge.
- Retention `RETENTION_SECONDS = 7 days`; alarm purges expired non-pending (and fails aged pending before delete rules).
- Never deletes `pending` younger than the retention window.

## Pending write-replay assumption

- Same key + same fingerprint while still `pending` (in flight): write path returns **404 `OPERATION_NOT_FOUND`** (contract maps in-flight lookup to 404; no success-shaped write body is invented).
- `GET /operations/{id}` also returns 404 for `pending`.

## Tasks

| ID | Status |
|----|--------|
| G-IDEM-1 Request field | Done — all write handlers gated |
| G-IDEM-2 Write-ahead record | Done — DO begin/complete + fingerprint |
| G-IDEM-3 Lookup route | Done — `GET /operations/:id` |
| G-IDEM-4 Errors | Done — catalogue + gateway/docs |
| G-IDEM-5 Fail closed stubs | Done — real txs finish records; no false completed |
| G-IDEM-6 Tests | Done — key/fingerprint/gate/operations |

## Commands run

- `npm run typecheck` — pass
- `npm run test:unit` — 255 passed
- `npm run verify-log-hygiene` — pass

## Not touched

- `app/**`, `docs/**` (parent contract), `.github/workflows/*.yml`

## Confirmation

No capability, signature, nonce, or raw bearer credential is stored in operation records or emitted by the log-hygiene scan of `gateway/src`.
