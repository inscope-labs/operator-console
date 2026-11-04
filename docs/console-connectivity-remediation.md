# Operator Console — Connectivity Remediation Plan

Eight stages to make network access deliberate, visible, and non-fatal.
Scope is the Android operator console (`app/`). Gateway Worker behaviour is unchanged.

## Stage 1 — Fail soft on transport and placeholders *(this PR)*

**Goal:** Never crash the process because DNS failed or the scaffold host was dialled.

- Treat `gateway.invalid` / empty base URL as **not configured** (`GatewayEndpoints.isConfigured`)
- `GatewayHttpClient` refuses to open a socket when not configured (`GatewayNotConfiguredException`)
- `callGateway` + `health()` map not-configured, `UnknownHostException`, and `IOException` → `AppResult.Failure`
- New failure states: `not_configured`, `unreachable`
- Unit tests for config detection and transport mapping

**Done when:** Launch with default config does not hit `GlobalExceptionHandler` for gateway DNS; dashboard can show an error state instead of dying.

## Stage 2 — Stop eager network on first paint

**Goal:** ViewModels must not assume a live gateway in `init`.

- `DashboardViewModel` / `PlanViewModel`: start `Idle` or `NotConnected`; load on explicit refresh or after connect
- Preserve pull-to-refresh / retry affordances
- Tests: ViewModel init performs zero repository calls when not configured

## Stage 3 — Configurable gateway base URL

**Goal:** Operators can set the real host without rebuilding.

- `GatewayBaseUrlStore` (DataStore) with default = unset
- Inject URL into `GatewayHttpClient` (no hard dependency on `GatewayEndpoints.BASE_URL` alone)
- Settings: edit, save, clear base URL
- Debug `BuildConfig` optional default (e.g. emulator `10.0.2.2:8787`) without shipping prod URLs in release source

## Stage 4 — Explicit connect + health gate

**Goal:** Connectivity is a user-visible action.

- Settings (or first-run): **Test connection** → `GET /health`
- Show `environment`, `version`, timestamp from contract §3.1
- Lab paths refuse non-`test` environment with clear copy
- Persist last successful health snapshot for the status banner

## Stage 5 — Connection status in the shell

**Goal:** Always know whether the console is talking to a gateway.

- App bar / banner: `Not configured` | `Unreachable` | `Connected (test|production)`
- Single `ConnectionStatusRepository` fed by health + config
- Deep link from banner to Settings

## Stage 6 — Transparent gateway activity log

**Goal:** Operators see what the app did on the network.

- Redacting logger in `GatewayHttpClient` (method, path, outcome; no tokens/capabilities)
- Settings diagnostic panel: last N events when diagnostics enabled
- Optional share/export for support (still redacted)

## Stage 7 — Client hardening

**Goal:** Predictable behaviour on bad networks.

- Connect/read timeouts on Ktor/OkHttp
- Cleartext only for debug LAN hosts via `network_security_config`
- Document mTLS still deferred; bearer remains sole identity

## Stage 8 — UX polish + regression gates

**Goal:** Lock the posture in tests and docs.

Regression gates: `ErrorMapperTest` (transport + V5–V8), `NotConfiguredUiTest` (banner + idle).
Operator help: [operator-connectivity.md](operator-connectivity.md). Architecture §2.2 session ≠ connectivity.


- UI/instrumentation: cold start with default config → no crash, not-configured UI
- Unit: transport mapping regressions
- Update `docs/architecture.md` / operator help: session ≠ connectivity; privileged ops still require a live gateway

---

## Non-goals

- Weakening capability / enrol security fail-closed rules
- Implementing offline plan cache (optional later)
- Changing Cloudflare Worker routes
