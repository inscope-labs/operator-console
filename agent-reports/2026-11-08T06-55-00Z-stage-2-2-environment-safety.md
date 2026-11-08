# Stage 2.2 Completion Report — Environment Safety

**Stage:** Phase 2, Stage 2.2: Environment safety `[AIS]`  
**Date:** 2026-11-08T06:55:00Z  
**Plan reference:** `RELEASE-PLAN.md` (v1.1), ADR 0002, ADR 0004, ADR 0007, B.4 item 1  
**Scope boundaries verified:** All changes confined to `app/**`, `docs/STATUS.md`, and `agent-reports/**`. Zero modifications to `gateway/**`.

---

## 1. Executive Summary

Stage 2.2 ("Environment safety") enforces complete isolation across operational environments (`test`, `staging`, `production`) in the Android Operator Console. Privileged capability minting and operations now fail closed when the configured expected environment does not match the environment reported by the gateway's `/health` probe. Operator credentials and hardware Keystore device signing keys are partitioned into strict per-environment slots, preventing any cross-environment credential leakage or key replay.

Full compilation (`compile_applet`) and the entire local JVM unit test suite (`316 tests across 50 test suites`) pass with 0 failures.

---

## 2. Tasks Executed & Verified

### T2-201: Expected Environment Configuration & Fail-Closed Gate
- **Persistence (`GatewayBaseUrlStore` & `PreferencesKeys`):**
  - Added `EXPECTED_ENVIRONMENT` DataStore key with `expectedEnvironmentFlow`, synchronous `expectedEnvironment()`, and suspend `readExpectedEnvironment()` / `saveExpectedEnvironment()`.
  - Enforced valid environment enumeration: `test`, `staging`, `production` (defaulting to `"test"`).
- **Fail-Closed Capability Minting (`ConfirmAndMintCapabilityUseCase`):**
  - Added `MintResult.EnvironmentMismatch(expected: String, reported: String)` and `EnvironmentMismatchException`.
  - Gated the single mint path: if expected environment does not match gateway-reported environment (or if health has not been probed), capability minting is refused with explicit operator feedback.
- **Connection Status Mapping (`ConnectionStatus` & `ConnectionStatusRepositoryImpl`):**
  - Extended `ConnectionStatus` with `data class EnvironmentMismatch(expected: String, reported: String)`.
  - Updated `ConnectionStatusRepositoryImpl` to observe `expectedEnvironmentFlow` and flag mismatches.
  - Updated `ConnectionStatusBanner` to render high-visibility error styling and a deep link to Settings when a mismatch occurs.
- **Settings UI (`SettingsViewModel` & `SettingsScreen`):**
  - Added interactive Expected Environment selector buttons (`test`, `staging`, `production`).
  - Added prominent `ErrorBanner` warning operators whenever an environment mismatch is detected.

### T2-202: Per-Environment Operator Credential Slots
- **Storage Isolation (`OperatorCredentialStore`):**
  - Updated `save(credential, environment = "test")`, `read(environment = "test")`, and `clear(environment = "test")`.
  - Keys are namespaced in encrypted Keystore-backed storage via `PreferencesKeys.operatorCredentialKey(environment)` (e.g. `operator_credential.staging`, `operator_credential.production`).
  - Added `clearAll()` method to purge all environment credential slots simultaneously.
  - Ensured switching environment in `GatewayBaseUrlStore` immediately switches active credential resolution in `NetworkModule` and `SettingsViewModel`, never reusing or falling back to another environment's credential.
- **Verification:** Unit tests in `OperatorCredentialStoreTest` and `SettingsViewModelTest` prove that saving a credential under `staging` leaves `production` and `test` unconfigured (`null`).

### T2-203: Credential Display Hygiene & Secret Protection
- **Display Protection (`SettingsScreen`):**
  - Credential input field uses `PasswordVisualTransformation()`.
  - Credential status is strictly binary ("Configured" vs "Not configured" per environment slot). The raw credential string is never displayed, exposed in state, or rendered in UI.
  - No clipboard copy button, reveal toggle, or prompt paste action is provided.
- **Logging Hygiene:**
  - `GatewayHttpClient` uses `GatewayActivityLog` and `Redactor` to redact authorization bearer headers (`Bearer [REDACTED]`).
  - No credential values appear in activity events or system logs.

### T2-204: Per-Environment Device Key & Enrolment Isolation (B.4 item 1)
- **Canonical Alias Derivation (`DeviceKeySpec.deriveAlias`):**
  - Implemented `DeviceKeySpec.deriveAlias(environment, host)` producing canonical aliases of form `op_<env>_<host>` (e.g. `op_staging_gateway_staging_internal`).
  - Normalizes URLs, removes protocols/ports, strips non-alphanumeric characters, and caps at 64 characters for Android Keystore compliance.
  - Empty or blank environments are refused with `IllegalArgumentException`.
- **Enrolment Isolation & Refusal (`DeviceEnrollmentStore`):**
  - Added `environment: String?` to `Record` with backwards-compatible newline serialization.
  - Partitioned active key tracking by environment slot (`active_kid.<env>`).
  - Strict refusal rule: `activeRecord(environment)` returns `null` if the record's environment does not match the target environment. A key enrolled for `staging` is **never** offered for `production`.
- **Enforcement in Single Mint Path:**
  - `ConfirmAndMintCapabilityUseCase` queries `enrollmentStore.activeRecord(expectedEnv)`. If a key was enrolled for staging but the app targets production, minting returns `MintResult.NotEnrolled`.

---

## 3. Test Coverage & Verification Evidence

1. **New & Updated Unit Tests:**
   - `DeviceKeyAliasTest.kt`: Validates canonical alias derivation, URL sanitization, environment distinctness (`op_staging_...` != `op_production_...`), Keystore 64-character limit capping, and blank environment rejection.
   - `DeviceEnrollmentStoreTest.kt`: Validates per-environment key enrollment, isolation across `staging`, `production`, and `test`, and verifies staging key refusal when targeting production.
   - `OperatorCredentialStoreTest.kt`: Validates per-environment credential slots, strict non-reuse across environments, and isolated clearing.
   - `ConfirmAndMintCapabilityUseCaseTest.kt`: Validates `EnvironmentMismatch` return and exception when expected environment differs from gateway reported environment or when unprobed; validates refusal when staging key is requested in production context.
   - `ConnectionStatusRepositoryImplTest.kt`: Validates `ConnectionStatus.EnvironmentMismatch` derivation when expected and reported environments diverge.
   - `SettingsViewModelTest.kt`: Validates environment switching, slot isolation without credential reuse, and environment mismatch detection from `/health` probe responses.
2. **Suite Execution:**
   - Command: `gradle :app:testDebugUnitTest`
   - Result: `BUILD SUCCESSFUL`, **316 tests completed, 0 failures, 0 errors**.
   - Compilation: `compile_applet` passed (`Build succeeded - the applet is compiled`).

---

## 4. Status Matrix & Next Steps

- `docs/STATUS.md` updated: Stage 2.2 marked **implemented**, tested, and safe to use.
- **Next Stage:** **Stage 2.3 — Pinning `[AIS]` code, `[CLAUDE]` for `network_security_config.xml` and CI** (T2-301 through T2-305).
