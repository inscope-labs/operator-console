# Error catalogue

Stable public error codes returned as JSON `{ "error", "message", "request_id" }`.

## Config

| Code | HTTP | Meaning |
|------|------|---------|
| `CONFIG_INVALID` | 503 | Environment validation failed; all routes except `/health` refuse work |

## Enrolment / attestation (GW-A addendum)

| Code | HTTP | Meaning |
|------|------|---------|
| `ENROLL_CHALLENGE_INVALID` | 400 | Challenge unknown, expired, or already used |
| `ENROLL_ATTESTATION_INVALID` | 400 | Bad chain, challenge binding, leaf/key mismatch, or claimed tier mismatch |
| `AUTH_TIER_NOT_ALLOWED` | 403 | Derived auth tier not in `ALLOWED_AUTH_TIERS` for this environment |

## Capability / guards / adapter

See `src/errors/catalogue.ts` for the full TypeScript union and HTTP status map (`OPERATOR_AUTH`, `UNKNOWN_KID`, `BAD_SIGNATURE`, `BRANCH_*`, `HEAD_MOVED`, `NONCE_REPLAYED`, `REPO_NOT_PRIVATE`, …).

Messages must never include secrets, keys, challenges, signatures, or nonces.
