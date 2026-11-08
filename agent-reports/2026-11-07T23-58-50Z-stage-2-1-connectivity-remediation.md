# Stage 2.1 — Connectivity Remediation Completion Report

**Date:** 2026-11-07T23:58:50Z  
**Stage:** Phase 2, Stage 2.1 (T2-101 … T2-106)  
**Author:** AI Studio Agent (`[AIS]`)  
**Scope:** Android Operator Console (`app/**`, `docs/**`)

---

## 1. Summary of Changes

Stage 2.1 completes the connectivity remediation (folding stages 2–8 of the superseded connectivity plan into `RELEASE-PLAN.md` Phase 2):

1. **T2-101 (Idle on Init):**
   - Verified that `DashboardViewModel` and `PlanViewModel` do not initiate network calls in `init`, starting cleanly in `UiState.Idle`.
   - Verified that 0 repository calls are performed when unconfigured or prior to explicit refresh.

2. **T2-102 (GatewayBaseUrlStore & URL Validation):**
   - Added `GatewayEndpoints.validateUrl` with strict policy validation:
     - Rejects blank/empty strings.
     - Rejects credentials embedded in URLs (`userInfo` like `user:password@`).
     - Rejects reserved placeholder hosts (`.invalid`).
     - Rejects query parameters and URL fragments.
     - Enforces HTTPS in release mode (`!isDebug`).
     - In debug mode (`isDebug = true`), restricts cleartext HTTP exclusively to local test hosts (`10.0.2.2`, `localhost`, `127.0.0.1`), rejecting remote cleartext hosts.
   - Added `validate()` helper in `GatewayBaseUrlStore`.
   - Injected `GatewayBaseUrlStore` into `GatewayHttpClient` with secondary constructor.
   - Connected URL validation to `SettingsViewModel.saveGatewayBaseUrl()` and surfaced detailed validation failure reasons in `SettingsScreen`.

3. **T2-103 (Explicit Test Connection & Health Snapshot):**
   - Verified explicit "Test connection" triggers `GET /health` without requiring credentials.
   - Displays environment, version, and server timestamp.
   - Persists the latest successful `GatewayHealthSnapshot` into `HealthSnapshotStore`; clears snapshot on failure or URL clear.

4. **T2-104 (Shell Status Banner & Staging Environment):**
   - Updated `ConnectionStatusBanner` and `strings.xml` to support `staging` (`Connected (staging)`) alongside `test` and `production`.
   - Added deep-link navigation from the banner directly to `SettingsScreen`.
   - Updated `ConnectionStatusRepositoryImplTest` to cover `staging`.

5. **T2-105 (Redacting Gateway Activity Log):**
   - Verified `GatewayActivityLog` and `GatewayActivityEvent` record method, path, and outcome only (excluding credentials, capabilities, nonces, and bodies).
   - Verified toggle, live update collection, clear, and system share-sheet export in `SettingsViewModel` and `SettingsScreen`.

6. **T2-106 (Client Hardening & Regression Gates):**
   - Configured OkHttp engine in `NetworkModule` with `followRedirects(false)` and `followSslRedirects(false)`.
   - In `GatewayHttpClient`, enforced strict refusal on 3xx redirects (throwing `GatewayHttpException`).
   - In `GatewayHttpClient`, enforced `MAX_RESPONSE_BYTES` (2 MiB) on advertised `Content-Length` headers and response body text (throwing `GatewayProtocolException`).
   - Created comprehensive unit tests in `GatewayHttpClientTest` and `SettingsViewModelTest`.
   - Added `testTag` identifiers to interactive elements in `SettingsScreen` per M3 guidelines.

---

## 2. Evidence of Verification

- `compile_applet`: **SUCCESS**
- `gradle :app:testDebugUnitTest`: **SUCCESS** (302 unit tests completed, 0 failures)
  - `GatewayEndpointsConfigTest`: 8 tests passed
  - `GatewayHttpClientTest`: 5 tests passed
  - `SettingsViewModelTest`: 10 tests passed
  - `ConnectionStatusRepositoryImplTest`: 5 tests passed
  - `DashboardViewModelTest`: 5 tests passed
  - `PlanViewModelTest`: 6 tests passed
  - `ErrorMapperTest`: 15 tests passed

---

## 3. Files Modified & Added

- `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/GatewayEndpoints.kt`
- `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/GatewayHttpClient.kt`
- `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/GatewayBaseUrlStore.kt`
- `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/OperatorCredentialStore.kt`
- `app/src/main/kotlin/com/inscopelabs/buildengine/di/NetworkModule.kt`
- `app/src/main/kotlin/com/inscopelabs/buildengine/ui/components/ConnectionStatusBanner.kt`
- `app/src/main/kotlin/com/inscopelabs/buildengine/ui/settings/SettingsViewModel.kt`
- `app/src/main/kotlin/com/inscopelabs/buildengine/ui/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/kotlin/com/inscopelabs/buildengine/core/network/GatewayEndpointsConfigTest.kt`
- `app/src/test/kotlin/com/inscopelabs/buildengine/core/network/GatewayHttpClientTest.kt` (new)
- `app/src/test/kotlin/com/inscopelabs/buildengine/ui/settings/SettingsViewModelTest.kt` (new)
- `app/src/test/kotlin/com/inscopelabs/buildengine/data/repository/ConnectionStatusRepositoryImplTest.kt`
- `docs/STATUS.md`

---

## 4. Next Stage

**Stage 2.2 — Environment Safety (`[AIS]`):**
- T2-201 Configured expected environment must equal gateway reported environment.
- T2-202 Per-environment operator credential slots.
- T2-203 Settings credential display & prompt hygiene.
- T2-204 Per-environment device key namespacing by environment and host.
