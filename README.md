# Operator Console

**The human-in-the-loop (HITL) control surface of the Build Engine: a native Android app (Kotlin) plus Operator Gateway ("Gateway A", a Cloudflare Worker), in one repository.**

> **Release target:** 0.6.0 (Standard). **Current state:** pre-release, partially implemented. See [Status](#status) and [`RELEASE-PLAN.md`](RELEASE-PLAN.md) before relying on anything here.

---

## ⚠ The Termux `cbridge` is discontinued

The Termux-based `cbridge` is **discontinued as the operator's control path**. It is replaced by:

1. **The Operator Console + Gateway A** (this repository), which carry every privileged operator action: publish, upload, reconcile, halt, resume, decide, sync, doctor, and the read views that precede them. The operator approves on the phone; the gateway alone enforces.
2. **CI workflows** (GitHub Actions in the consumer repository and in this one), which run verification, generated-file checks and deployment gates on a runner instead of on the operator's phone.

What this means in practice:

- **No new development** goes into `cbridge`, and no document in this repository should send an operator to it as the way to act.
- **Released engine artifacts** (for example build-engine 0.5.0) still *contain* `cbridge`, because they are immutable. Treat it there as legacy, not as the supported path for 0.6.0.
- **Each `cbridge` command has a named replacement** (table below). A command is only *removed from operator practice* once the operator has signed its row in the cbridge coverage matrix (Plan Stage 6.4 / 8.4). Until the replacement for a row is `implemented`, the status table below says so; do not read "discontinued" as "replacement complete".

| Former `cbridge` / Termux action | Replacement |
|---|---|
| `status`, `next`, `plans` (read) | Console read screens → Gateway A |
| `publish` | Console confirmation → minted capability → Gateway A guarded publish |
| `upload` | Console document picker → Gateway A upload |
| `reconcile`, `halt`, `resume`, `sync`, `doctor` | Console → Gateway A |
| Operator decisions on the framework | Console decision screen (attribution, rationale, scope, evidence) → Gateway A audit record |
| `check`, `render --check`, `verify` (plan-supplied commands) | **CI workflows** on a runner; results are shown in the console where the gateway exposes them. Never on the phone, never in the gateway (it has no subprocess) |
| `bootstrap`, `init` (create repositories and branches) | **Outside the app** by design (build-engine tooling or CI). The console never creates repositories |

---

## What this is

The Build Engine coordinates plans, work orders and AI-agent submissions across consumer repositories. **Humans stay in the loop at the points where authority matters.** This repository is where the human operator exercises that authority:

- **Inspect** plans, tasks, readiness, blockers, submissions and gateway health.
- **Decide** and **approve** consequential actions, one at a time.
- **Halt** and **resume** work, with attribution and a recorded reason.
- **Review** agent submissions and send them back, accept them, or transfer them.
- **Govern** access: enrolled devices, revocation, audit inspection.

It is deliberately a *thin, credential-minimal* client. The console expresses intent; **the gateway is the only component that enforces.**

| Component | Role | Location |
|---|---|---|
| **Operator Console** | Sole seat of operator authority. Represents human intent. **Untrusted for enforcement.** | `app/` |
| **Gateway A** (Cloudflare Worker) | Sole enforcement point for operator actions. Verifies capabilities, runs the guard set, holds the narrow GitHub credential, writes the audit record. | `gateway/` |
| **Gateways B / C** | Class B agent read and write gateways (context links, submissions). | `inscope-labs/build-engine` |

> Earlier revisions of this README said the gateway was a separate project. **Gateway A lives in this repository** (`gateway/`). Only Gateways B and C live in build-engine.

### The one property that shapes everything

> **No operator gets full repository access: only the minted branch.**

- The app holds **no GitHub credential of any kind**: no PAT, OAuth token or GitHub App credential. Nothing repository-scoped on the device can be stolen.
- It holds a **non-exportable device key** (Android Keystore; StrongBox where the device really has it) that signs capabilities, and a **gateway-scoped operator credential** that authorises the operator surface and grants no repository access.
- It **mints a scoped, expiring capability per action** (`ctxop-v1`: operation, branch, exact head SHA, single-use nonce) **only after the operator confirms**. No capability exists before the operator decides.
- The gateway keeps its own narrow GitHub credential. Neither side needs the other's.

Rationale: [`docs/adr/0002-no-repository-credentials-on-device.md`](docs/adr/0002-no-repository-credentials-on-device.md).

---

## The operator's role (HITL synopsis)

**The operator does:**

| Responsibility | How it is enforced |
|---|---|
| Confirm every consequential action individually | The summary shows the operation, plan, task or order, branch and the **full 40-character head SHA**. Approval produces a signature with the device key; there are no pre-approved or batch approvals. |
| Confirm with the gesture the gateway allows | Two tiers: `hardware_confirm` (biometric/device-credential prompt bound to the key) and `in_app_confirm` (type the first 7 hex characters of the expected head). A device without biometrics can still operate; the gateway's policy decides which operations accept which tier, and the tier is written to the audit record. |
| Act on current state only | Capabilities bind to the exact head SHA and expire in minutes. A moved head, replayed nonce, expired capability or active halt is refused by the gateway. |
| Treat an unknown outcome as unknown | A request that was sent but not acknowledged is *uncertain*, never "succeeded" or "failed". It is resolved by looking the operation up, never by blind retry. |
| Record decisions | Attribution, rationale, scope and evidence are required fields. |
| Keep the environment straight | The console refuses privileged actions when the gateway's reported environment differs from the configured one. Keys are per environment. |

**The operator never:**

- pastes a GitHub token, signing key, capability or nonce into the app, an issue, a log or an AI chat;
- uses the Lab (test harness) against anything but a `test` gateway;
- runs verification commands from the phone;
- treats a debug or unsigned build as an authority client.

---

## Who should read what

| You are… | Start here |
|---|---|
| **A consumer / operator** (you use the Build Engine and approve work) | [The operator's role](#the-operators-role-hitl-synopsis), [Using the console](#using-the-console), `docs/operator-guide.md` |
| **An AI agent** working in this repository | [Rules for agents](#rules-for-agents) (read fully before editing) |
| **A developer** | [For developers](#for-developers) |
| **A security reviewer** | `docs/threat-model.md`, `docs/capability-format.md`, `docs/gateway-contract.md`, ADRs in `docs/adr/` |

---

## Using the console

> The console is **not released yet**. The steps below describe the intended 0.6.0 flow; do not use a debug build for real operations.

1. **Install** the signed release APK from the GitHub Release (checksum and signature published with it). Sideload; the app requests only `INTERNET` and `USE_BIOMETRIC`.
2. **Configure** the gateway URL in Settings. A fresh install is *Not configured* and makes **no network calls**. Release builds accept HTTPS only.
3. **Test connection.** Preflight reports reachability, TLS/pin identity, authentication, clock skew, contract compatibility, enrolment and the security posture the gateway accepted.
4. **Enrol the device.** The gateway issues a challenge; the app generates a non-exportable key for this environment and sends its attestation. The posture screen states what is actually true (hardware or software backing, which tier).
5. **Operate.** Inspect plans, then act: each action shows its summary, waits for your confirmation, mints one capability, and reports the gateway's real outcome with an audit reference.
6. **Recover.** If the app dies or the network drops mid-operation, it resolves the outcome on restart by asking the gateway; it never resends on its own.

---

## Security posture and deliberate absences

Their absence is a security property, not an oversight. A PR that adds any of them needs a very good reason.

- ❌ Any GitHub token, PAT, OAuth token or GitHub App credential on the device or in preferences.
- ❌ A `verify` screen or `verify.argv` execution path (verification runs in CI).
- ❌ `bootstrap` / `init` (repository and branch creation).
- ❌ A local git worktree, a shell bridge, or `Runtime.exec`.
- ❌ Durable audit storage on the device (the audit trail lives in the private consumer repository).
- ❌ Session tokens persisted across launches (the session is in memory; the capability TTL is the real bound).
- ❌ Backups of app data (`allowBackup=false`); `FLAG_SECURE` on the window.
- ❌ Lab code or its HMAC signer in release builds.

Transport: pinning and rotation policy in `docs/adr/0007-production-transport-posture.md`; mTLS is deferred (`0006-mtls-deferred.md`). Where this README says "pinned", check [Status](#status) for whether it is implemented yet.

---

## Status

Status words, used identically across the Build Engine: **implemented**, **reference only**, **scaffold**, **not started**, **not deployed**, **unsupported**. Snapshot of **5 November 2026**; the authoritative, current table is `docs/STATUS.md`, and the path to release is [`RELEASE-PLAN.md`](RELEASE-PLAN.md).

| Area | Status |
|---|---|
| Contract, ADRs, conformance vectors | implemented |
| Capability model, codec, signers, minter | implemented (per agent report) |
| Device key, enrolment, confirmation tiers (app side) | implemented (per agent report) |
| Read layer and read screens | implemented (per agent report) |
| Connectivity and settings | partial |
| Write operations (publish, upload, reconcile, halt, resume, decide), operation journal, recovery | scaffold / not started |
| Certificate pinning | scaffold |
| Lab (test harness) | not started |
| Gateway A | partial; **not deployed** |
| Gateway CI and deploy gates | being hardened (currently permissive) |
| Termux `cbridge` replacement overall | **in progress**; `cbridge` is discontinued, replacement not complete |

Nothing here may be described as "production ready, deployed and supported" until the release plan's Phase 7 evidence exists.

---

## Rules for agents

Applies to AI Studio and any other automated contributor.

1. **Read first:** `RELEASE-PLAN.md`, `docs/STATUS.md`, the stage you were authorised to run, and the ADRs it cites. Do only that stage; touch only the paths it lists.
2. **Protected paths** are not yours: `.github/workflows/*.yml`, `app/proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `version.properties`, `network_security_config.xml`, `build-logs/**`. Report what needs changing; a human-reviewed PR makes the change.
3. **Required credentials** The operator will provide the required GitHub PAT, OAuth token, App credential, signing material, operator credential, capability or nonce required to complete each task. Request one if none is provised. 
4. **Do not invent** endpoints, fields, error codes or operations. The wire authority is `docs/gateway-contract.md` and its schemas. If it is silent, stop and report.
5. **Fail closed.** Missing configuration, unavailable state, ambiguous policy or a stale head means refusal, not a default.
6. **Never report success on a stub.** A placeholder returns "not implemented", never `ok`. Remove the `SCAFFOLD PLACEHOLDER` marker and its `TODO(...)` when you replace one.
7. **"Unverified" is not "done".** You cannot run `npm install` or release builds; CI is the verifier. A stage closes when CI is green on the pushed commit.
8. **Do not use `cbridge`** or direct operators to it. Do not add `bootstrap`, `init`, `verify`, shell or subprocess paths to the app or the gateway.
9. **Write the report** `agent-reports/<UTC-ISO>-<slug>.md` for every stage: what changed, what was verified and how, what was not, and any stop-and-ask.
10. **You push to `main`.** Work alone on your stage; unrelated edits overwrite concurrent work.

---

## For developers

### Layout

```
operator-console/
├── RELEASE-PLAN.md                 # authoritative plan to 0.6.0; phases, gates, decisions
├── directory-structure.md          # structural spec the Android tree follows
├── app/                            # Android client (Kotlin, Compose, Hilt)
│   └── src/{main,test,androidTest}/kotlin/com/inscopelabs/buildengine/
│       ├── core/        # crypto, security, network, time, result
│       ├── capability/  # ctxop-v1: model, codec, signing, minter
│       ├── data/        # gateway client, local stores, repository impls
│       ├── domain/      # models, repository interfaces, use cases
│       ├── ui/          # Compose screens, navigation, components
│       ├── boot/ diagnostics/ lab/ vectors/ di/ util/
├── gateway/                        # Gateway A (Cloudflare Worker, TypeScript)
│   ├── src/{routes,guards,capability,enrolment,attestation,adapter,
│   │        durable-objects,audit,auth,config,errors,util}
│   ├── tests/{unit,conformance,helpers}
│   └── docs/                       # gateway architecture, guard set, error catalogue
├── docs/
│   ├── gateway-contract.md         # wire contract (authoritative)
│   ├── capability-format.md        # ctxop-v1
│   ├── device-enrollment.md  threat-model.md  architecture.md
│   ├── fixtures/                   # conformance vectors
│   └── adr/                        # decision records
├── agent-reports/                  # one report per agent stage
└── .github/workflows/              # android-ci, build-apk-debug, gateway CI/deploy
```

> The gateway workflows are currently named `cbridge-ci.yml` / `cbridge-deploy.yml`. That is a legacy name: they build and deploy **Gateway A**, not the discontinued Termux `cbridge`. They are being renamed `gateway-ci.yml` / `gateway-deploy.yml` (Plan T1-102).

### Build and test (Android)

Requires **JDK 17+** and the **Android SDK** (platform and build-tools 36).

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug               # debug APK
./gradlew :app:testDebugUnitTest           # JVM unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumented (device/emulator)
```

Toolchain is pinned in `gradle/libs.versions.toml` (Gradle, AGP, Kotlin, KSP, Hilt, Compose BOM; `compileSdk`/`targetSdk` 36, `minSdk` 28). Treat that file as the source of truth for versions. `minSdk 28` is the Keystore floor. StrongBox needs Android 12+ **and** a secure element; many devices are software-backed, and the console must report what is true.

### Build and test (Gateway A)

```bash
cd gateway
npm ci
npm run typecheck && npm test
```

The wrangler configuration is generated from `wrangler.toml.template`; secrets (Cloudflare, GitHub App, operator credential, attestation roots, signing keys) live only in GitHub environments, never in the repository. Environments are `test` (local/throwaway), `staging` and `production`; Lab and HMAC capabilities are accepted **only** on `test`.

### Ground rules

- Contract first: change `docs/gateway-contract.md` and the schemas before code; regenerate vectors for any wire change.
- Update the console docs, gateway docs and build-engine docs together when an operation, field, endpoint or release class changes.
- Architecture tests guard the boundaries (single mint path, no token patterns, no process execution). Do not weaken them.
- Releases come from a tag on a CI-green commit, built and signed by the release workflow. `main` is intentionally unprotected so automated agents can push; compensating controls are in `RELEASE-PLAN.md` (BD-0.5).

### Documentation map

| Need | Document |
|---|---|
| Where are we, what is released | `docs/STATUS.md` |
| What remains, in what order, what blocks | `RELEASE-PLAN.md` |
| Operator duties in detail | `docs/operator-guide.md` |
| Wire protocol | `docs/gateway-contract.md`, `docs/capability-format.md` |
| Why it is built this way | `docs/adr/` |
| Risks | `docs/threat-model.md` |

---

## Related repositories

| Repository | What it is |
|---|---|
| `inscope-labs/build-engine` | The engine, the plan of record, Class B read/write gateways, the guard set, the compatibility record |
| `inscope-labs/castiel` | The AMK framework specification |

## Versioning

The console and Gateway A are versioned and released together (0.6.0 target) and recorded in the Build Engine compatibility record. The protocol identifier and capability version are defined in `docs/capability-format.md`.
