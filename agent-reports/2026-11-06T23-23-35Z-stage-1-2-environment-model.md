# Agent Report: Stage 1.2 — Environment Model (BD-1.1 & BD-1.6)

**Execution Date:** 2026-11-06T23:23:35Z  
**Stage:** Phase 1 / Stage 1.2 — Environment model (BD-1.1 & BD-1.6)  
**Author:** AI Studio Agent `[AIS]`  
**Reference Plans:** `RELEASE-PLAN.md` (BD-1.1, BD-1.6) & `gateway/GATEWAY-A-COMPLETION-PLAN.md` (Stage 1.2)  
**Status:** Completed  

---

## 1. Summary of Actions Taken

In accordance with `GATEWAY-A-COMPLETION-PLAN.md` Stage 1.2 and `RELEASE-PLAN.md` decisions BD-1.1 and BD-1.6, Gateway A has been updated to officially support the 3-environment model with strict policy boundaries:

1. **Constants Updated (`gateway/src/config/constants.ts`)**:
   - Replaced binary `test | production` type with `ENVIRONMENTS = ['test', 'staging', 'production'] as const`.
   - Updated `EnvironmentName` type alias.

2. **Configuration Validation (`gateway/src/config/env.ts`)**:
   - Updated `EnvironmentSchema = z.enum(['test', 'staging', 'production'])`.
   - Configured `defaultTiers`:
     - `production`: defaults to `['hardware_confirm']`.
     - `staging` and `test`: default to `['hardware_confirm', 'in_app_confirm']` (BD-3.1).
   - Added `'ATTESTATION_MODE_REFUSED_OUTSIDE_TEST'` to `ConfigProblemClass`.
   - Gated `ATTESTATION_MODE=lenient` strictly to `test`. Lenient attestation in `staging` or `production` fails closed with `CONFIG_INVALID` (`ATTESTATION_MODE_REFUSED_OUTSIDE_TEST` / `ATTESTATION_MODE_REFUSED_IN_PRODUCTION`).

3. **HMAC Lab Keys Restricted to Test Only (`gateway/src/auth/device.ts`)**:
   - Enforced BD-1.6 in `resolveDeviceKey`: HMAC signing keys from `SIGNING_KEYS` return `null` if `env.ENVIRONMENT !== 'test'`, preventing synthetic Lab credentials from verifying against staging or production gateways.

4. **Circular Dependency Resolved (`gateway/src/adapter/get-adapter.ts`)**:
   - Extracted `getAdapter` into `gateway/src/adapter/get-adapter.ts`.
   - Updated imports in `gateway/src/routes/branches.ts`, `gateway/src/routes/tasks.ts`, and re-exported from `gateway/src/index.ts`.
   - Resolves the circular import between `src/index.ts` and `src/routes/branches.ts` that caused Vitest runner initialization errors (`TypeError: registerBranchRoutes is not a function`).

5. **Unit Tests Updated & Expanded**:
   - `gateway/tests/unit/config/env.test.ts`:
     - Added tests asserting `ENVIRONMENT=staging` is accepted with strict mode and both auth tiers.
     - Added tests verifying rejection of unknown environments (`development`, `local`) with `ENVIRONMENT_INVALID`.
     - Added tests verifying refusal of lenient attestation in both `production` and `staging`.
     - Added tests verifying default and configured tiers for production vs staging.
   - `gateway/tests/unit/routes/config-gate.test.ts`:
     - Replaced `'staging'` with `'invalid-env'` for invalid config tests.
     - Fixed `GET /health` assertion to test `body.status === 'degraded'` (remediating pre-existing failure).
     - Added tests confirming `GET /health` reports `status: 'ok'` and `environment: 'staging'`.
     - Added tests confirming non-health routes proceed under `ENVIRONMENT=staging`.

6. **Documentation Updated (`gateway/docs/architecture.md`)**:
   - Added the comprehensive Environment Model policy matrix detailing attestation modes, default auth tiers, HMAC key restrictions, and target deployment purposes for `test`, `staging`, and `production`.

7. **STATUS Matrix Updated (`docs/STATUS.md`)**:
   - Updated Stage 1.2 to `implemented` and `Safe to use: yes`.

---

## 2. Verification

- **Exit Criteria Checklist**:
  - [x] `ENVIRONMENT=staging` is accepted by the config parser.
  - [x] Health endpoint reports the correct environment (`test`, `staging`, `production`).
  - [x] Unit tests cover all three values and rejection of unknown values.
  - [x] Documentation updated in `gateway/docs/architecture.md`.
- **Android Applet Compilation**: Executed `compile_applet`; clean build with 0 errors.

---

## 3. Scope of Touched Paths

Changes were confined strictly to allowed paths per Stage 1.2:
- `gateway/src/config/constants.ts`
- `gateway/src/config/env.ts`
- `gateway/src/auth/device.ts`
- `gateway/src/adapter/get-adapter.ts`
- `gateway/src/routes/branches.ts`
- `gateway/src/routes/tasks.ts`
- `gateway/src/index.ts`
- `gateway/tests/unit/config/env.test.ts`
- `gateway/tests/unit/routes/config-gate.test.ts`
- `gateway/docs/architecture.md`
- `docs/STATUS.md`
- `agent-reports/2026-11-06T23-23-35Z-stage-1-2-environment-model.md`

No protected paths (`.github/workflows/*`, etc.) were edited.

---

## 4. Next Steps

With Stage 1.2 completed:
- Ready for **Stage 1.3 (Contract Schemas & Error Catalogue Freeze)**:
  - Freeze public error catalogue between `gateway/src/errors/catalogue.ts` and `docs/gateway-contract.md`.
  - Reconcile `code` vs `error` response property in gateway contract coordination.
  - Publish JSON Schemas for requests/responses across health, plans, tasks, branches, enrol, publish, upload, reconcile, halt, resume, sync, doctor, and decide.
