# Agent Report: Stage 1.2 — Environment model (BD-1.1)

- **Stage**: GATEWAY-A-COMPLETION-PLAN — Phase 1, Stage 1.2 (BD-1.1)
- **Timestamp (UTC)**: 2026-11-07T01-48-46Z
- **Slug**: stage-1-2-environment-model
- **Branch**: `stage/1-2-environment-model`

---

## 1. What this stage changed

`ENVIRONMENT` is now accepted as one of `{test, staging, production}`.

Previously only `test` and `production` were valid; `staging` produced
`ENVIRONMENT_INVALID` and blocked every non-health route with 503.

### Policy (unchanged semantics for production)

| Environment | Default auth tiers | Lenient attestation |
|-------------|--------------------|---------------------|
| test | hardware_confirm, in_app_confirm | Allowed |
| staging | hardware_confirm, in_app_confirm | Allowed |
| production | hardware_confirm only | Refused |

## 2. Exit criteria

| Criterion | Status |
|---|---|
| `ENVIRONMENT=staging` accepted by config parser | ✅ |
| Health endpoint reports the correct environment | ✅ |
| Unit tests cover all three values and rejection of unknown | ✅ |
| Documentation updated | ✅ |

## 3. Files changed

- `gateway/src/config/constants.ts`
- `gateway/src/config/env.ts`
- `gateway/tests/unit/config/env.test.ts`
- `gateway/tests/unit/routes/config-gate.test.ts`
- `gateway/docs/architecture.md`
- this report

## 4. Statement of completion

Stage 1.2 is complete pending CI green and operator review of the PR.
