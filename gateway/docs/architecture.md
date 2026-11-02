# Architecture mapping

Maps packages under `src/` to the cbridge specification and the GW-A repair work.

| Package | Spec / stage | Notes |
|---------|----------------|-------|
| `config/` | GW-2 | zod-validated env; `ENVIRONMENT` ∈ {test, production}; lenient attestation refused in production |
| `capability/` | §8 | ctxop-v1; Model B preferred; tier is a property of the kid, not the wire |
| `adapter/` | §11 | MultiRepoGitHubAdapter |
| `guards/` | §10 | G1, G3, G4, G13 wired; remaining guards later |
| `routes/` | §9 | Read + publish stub; enrolment routes in later stage |
| `durable-objects/` | §12 | NonceStore exported; **EnrolmentRegistry** (challenges + kids) next stage |
| `attestation/` | GW-5 | KeyDescription / chain verify (next stage) |
| `audit/` | §13–14 | Redaction gate |
| `errors/` | §15 + addendum | Includes `CONFIG_INVALID`, enrol codes |
| `util/` | — | Clock, logger (redacting), Result |

## Configuration fail-closed

`parseGatewayConfig` runs at the start of each request (via router). On failure, every path except `GET /health` returns **503 CONFIG_INVALID**. `/health` reports problem *classes* only (no secret values).

## Enrolment registry (planned)

Durable Object storing single-use challenges (TTL 300s) and enrolment records `{ kid, alias, publicKey, authTier, status, enrolledAt, leafCertSha256 }`. Internal fetch routes only; no public admin surface.
