# Error catalogue

Stable public error codes returned as JSON `{ "error", "message", "request_id" }`.

## Config

| Code | HTTP | Meaning |
|------|------|---------|
| `CONFIG_INVALID` | 503 | Environment validation failed; all routes except `/health` refuse work |

## Not implemented (fail-closed stub)

| Code | HTTP | Meaning |
|------|------|---------|
| `NOT_IMPLEMENTED` | 501 | The operation is in the contract but is not implemented in this gateway build. The request was refused before any work was performed: nothing was published, uploaded, reconciled, halted or resumed. |

`NOT_IMPLEMENTED` is never returned with a 2xx status and its body never claims
success. See ADR 0002 — *Fail-closed stub refusal*.

## Enrolment / attestation (GW-A addendum)

| Code | HTTP | Meaning |
|------|------|---------|
| `ENROLL_CHALLENGE_INVALID` | 400 | Challenge unknown, expired, or already used |
| `ENROLL_ATTESTATION_INVALID` | 400 | Bad chain, challenge binding, leaf/key mismatch, or claimed tier mismatch |
| `AUTH_TIER_NOT_ALLOWED` | 403 | Derived auth tier not in `ALLOWED_AUTH_TIERS` for this environment |

## Implementation status

| Code | HTTP | Meaning |
|------|------|---------|
| `NOT_IMPLEMENTED` | 501 | Operation, transaction, or route not yet implemented; fail closed |

## Capability / guards / adapter

See `src/errors/catalogue.ts` for the full TypeScript union and HTTP status map (`OPERATOR_AUTH`, `UNKNOWN_KID`, `BAD_SIGNATURE`, `BRANCH_*`, `HEAD_MOVED`, `NONCE_REPLAYED`, `REPO_NOT_PRIVATE`, …).

Messages must never include secrets, keys, challenges, signatures, or nonces.
