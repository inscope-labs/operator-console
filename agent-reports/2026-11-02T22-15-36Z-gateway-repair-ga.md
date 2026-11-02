# gateway-repair-ga — Stages 2–3 (GW-2 config, GW-8 catalogue/docs)

Timestamp: 2026-11-02T22-15-36Z
Branch: feat/gateway-repair-ga-stage2-3
Base: main (post PR #15 typecheck clean)

## Tasks completed

### GW-2 Config
- `config/env.ts`: zod parser; ENVIRONMENT ∈ {test, production} only; defaults for ALLOWED_AUTH_TIERS; ATTESTATION_MODE strict|lenient with lenient refused in production; ATTESTATION_ROOT_SHA256 hex array; binding presence flags (no secret values).
- `config/constants.ts`: contract version, challenge TTL, auth tiers, environment names.
- `util/clock.ts`, `util/logger.ts` (redacting), `util/result.ts`.
- Router config gate: non-health → 503 CONFIG_INVALID; GET /health always answers with problem classes when invalid.
- `routes/health.ts`: unauthenticated; optional bearer ignored for gating.
- `wrangler.toml.template`: documents new env vars.
- `index.ts`: Env fields + registerHealthRoutes.

### GW-8 Docs / catalogue
- `errors/catalogue.ts`: CONFIG_INVALID (503), ENROLL_CHALLENGE_INVALID (400), ENROLL_ATTESTATION_INVALID (400), AUTH_TIER_NOT_ALLOWED (403).
- `docs/error-catalogue.md`, `docs/architecture.md` updated.

## Files touched (gateway/ only, plus this report)

- gateway/src/config/env.ts, constants.ts
- gateway/src/util/clock.ts, logger.ts, result.ts
- gateway/src/errors/catalogue.ts
- gateway/src/routes/router.ts, health.ts
- gateway/src/index.ts
- gateway/wrangler.toml.template
- gateway/scripts/generate-wrangler.ts (preserve _env)
- gateway/docs/error-catalogue.md, architecture.md
- gateway/tests/unit/config/env.test.ts
- gateway/tests/unit/routes/config-gate.test.ts
- agent-reports/2026-11-02T22-15-36Z-gateway-repair-ga.md (permitted root write)

## Tests
- Added unit tests for parseGatewayConfig and config gate.
- **Unverified in-agent:** `npx tsc --noEmit` and `npx vitest run tests/unit` (no npm install per rules).

## Unverified
- Full typecheck and unit suite execution in CI/agent environment.
- Interaction with existing router tests that construct Env without ENVIRONMENT (should default via parse — ENVIRONMENT missing is invalid; tests may need ENVIRONMENT=test). Existing router.test.ts may now get CONFIG_INVALID if ENVIRONMENT unset — **stop-and-ask / follow-up:** existing tests must pass ENVIRONMENT=test (or we treat missing ENVIRONMENT as test only in test builds). Current parse treats missing ENVIRONMENT as ENVIRONMENT_INVALID.

## Correction applied during implementation
Missing ENVIRONMENT is currently **invalid** (fail closed). Existing unit tests that omit ENVIRONMENT will receive CONFIG_INVALID. A follow-up micro-fix may set default ENVIRONMENT only when explicitly under vitest, or update existing tests. Flagged as stop-and-ask if CI fails on prior router tests.

## Stop-and-ask
1. Should missing ENVIRONMENT default to `test` in non-production Workers, or remain invalid (current)? Contract says reported value is test|production; fail-closed favours invalid.
2. Stages 4+ (registry DO, attestation, enroll routes) not started.

## Out of scope (unchanged stubs)
halt/resume/sync/doctor/decisions, G5+, transaction, PlanDO, mTLS, package.json/lock, workflows.
