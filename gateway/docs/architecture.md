# Architecture mapping

Maps packages under `src/` to the cbridge specification and the GW-A repair work.

| Package | Spec / stage | Notes |
|---------|----------------|-------|
| `config/` | GW-2 / Stage 1.2 | zod-validated env; `ENVIRONMENT` ∈ {test, staging, production}; lenient attestation and HMAC lab keys refused outside test (BD-1.1, BD-1.6) |
| `capability/` | §8 | ctxop-v1; Model B preferred; tier is a property of the kid, not the wire |
| `adapter/` | §11 | MultiRepoGitHubAdapter; getAdapter extracted to break circular imports |
| `guards/` | §10 | G1, G3, G4, G13 wired; remaining guards in Phase 2 |
| `routes/` | §9 / Stage 1.1 | Read + fail-closed stubs (501 NOT_IMPLEMENTED); enrolment routes |
| `durable-objects/` | §12 | NonceStore exported; EnrolmentRegistry (challenges + kids) |
| `attestation/` | GW-5 | KeyDescription / chain verify; strict mode default |
| `audit/` | §13–14 | Redaction gate |
| `errors/` | §15 + addendum | Includes `CONFIG_INVALID`, enrol codes, `NOT_IMPLEMENTED` |
| `util/` | — | Clock, logger (redacting), Result |

## Environment model (BD-1.1)

The gateway recognizes three closed environments:

| Environment | Attestation Mode | Default Auth Tiers | Lab HMAC Keys (`SIGNING_KEYS`) | Purpose |
|---|---|---|---|---|
| `test` | `strict` or `lenient` | `hardware_confirm`, `in_app_confirm` | Allowed | Local unit tests and automated conformance |
| `staging` | `strict` only (`lenient` refused) | `hardware_confirm`, `in_app_confirm` | **Refused** (BD-1.6) | Pre-production testing on real Android hardware |
| `production` | `strict` only (`lenient` refused) | `hardware_confirm` only | **Refused** (BD-1.6) | Canonical production control plane |

Lenient attestation mode (`ATTESTATION_MODE=lenient`) allows unverified certificate chains and is **strictly prohibited outside `test`**. Any configuration attempting lenient mode in `staging` or `production` fails closed with `CONFIG_INVALID` (`ATTESTATION_MODE_REFUSED_OUTSIDE_TEST` / `ATTESTATION_MODE_REFUSED_IN_PRODUCTION`).

HMAC signing keys from `SIGNING_KEYS` are used exclusively by Lab test tooling and are rejected at runtime if the gateway's `ENVIRONMENT` is not `test`.

## Configuration fail-closed

`parseGatewayConfig` runs at the start of each request (via router). On failure, every path except `GET /health` returns **503 CONFIG_INVALID**. `/health` reports problem *classes* only (no secret values).

