# Operator Console — v0.6.0 RELEASE PLAN

**Repository:** `inscope-labs/operator-console` (Android app `app/` + Operator Gateway "Gateway A" `gateway/`)
**Plan revision:** 1.0 — DRAFT for operator review
**Review baseline:** 5 November 2026 (`main` as cloned on that date; I did not run any build or test)
**Supersedes:** `docs/V0.6.0-BUILD-PLAN.md` (OC-0…OC-9), the unfinished parts of `IMPLEMENTATION-PLAN.md` (Phases 4.3–6, kept in `public/assets/aistudio/IMPLEMENTATION-PLAN_c11dc242.md`), `docs/console-connectivity-remediation.md` stages 2–8, and the open stop-and-ask items in the `gateway-repair-ga` agent reports.
**Companion:** `inscope-labs/build-engine` → `docs/release/V0.6.0-BUILD-PLAN.md` (Gateways B/C, conformance, compatibility record).
**Operator guide:** `operator-console-build-engine-operator-guide-v2_0.md` — verified in Part B; corrections to be published as v2.1.

> Nothing in this plan is approved until the operator closes the blocking decisions of a phase (Part C) and signs the phase gate. AI Studio executes one stage at a time, only after the operator authorizes that stage.

---

## Part A — Release objective, scope, rules

### A.1 Objective

Release **Operator Console 0.6.0**: a native Android client plus Gateway A, supported as the operator control surface that replaces the Termux `cbridge` for the operations listed in the (to-be-completed) cbridge coverage matrix, proven by an end-to-end run on the real acceptance device against deployed staging gateways.

Two acceptance levels, never conflated:

- **A. Console complete:** every operation the release advertises works against deployed staging Gateway A, fails closed, survives crash/timeout, and is covered by executed tests.
- **B. System complete:** the joint pilot with build-engine BE-7 (Gateways A, B, C) passes, and the operator signs the Termux retirement per command.

Release claims use the status vocabulary of the guide §1.3: **implemented, reference only, scaffold, not started, not deployed, unsupported**. No release note may say "production ready, deployed, and supported" (the conditional wording in build-engine ADR 0006 item 4) until the gates in Phase 7 have passed with archived evidence.

### A.2 Ownership boundary

| Element | Owner | Notes |
|---|---|---|
| Android app code, tests, docs in allowed paths | AI Studio agent `[AIS]` | One stage per run; pushes directly to `main` (ADR 0010) |
| Gateway A code (`gateway/src`, `gateway/tests`, `gateway/docs`) | `[AIS]` or Claude | The AI Studio agent cannot run `npm install`; **CI is the only verifier** (see Stage 1.1) |
| Protected paths: `.github/workflows/*.yml`, `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `version.properties`, `build-logs/**` | Claude via branch + PR, operator merges `[CLAUDE]` | Also `gradle/libs.versions.toml` and `network_security_config.xml` by convention in this plan |
| Decisions, secrets, Cloudflare/GitHub environment setup, device tests, approvals, publication | Operator `[OP]` | Sole human authority |
| Build-engine contracts, Gateways B/C, compatibility record | build-engine repo `[BE]` | Consumed here; not edited here |

### A.3 Execution rules (apply to every stage)

- **G-01** Standing AI Studio conventions: no release-build verification, no arbitrary shell setup, no HEAD drift-checks (drift failures return to Claude for hash correction); protected paths are not edited by the agent; mandatory report `agent-reports/<UTC-ISO>-<slug>.md`.
- **G-02** One stage per run; touch only the paths listed for the stage. AI Studio pushes its whole working tree, so unrelated edits silently overwrite out-of-band work: Claude rebases any open PR branch before each stage, one writer per branch.
- **G-03** `main` stays unprotected (ADR 0010). Compensating controls are in BD-0.5.
- **G-04** No claim without evidence: a gate passes only when the command ran and its output was inspected. A report line "unverified in-agent" is **not** a pass; the stage is closed only when CI shows green on the pushed SHA.
- **G-05** Fail closed on missing configuration, unavailable state, ambiguous policy, stale head, unverifiable input.
- **G-06** No GitHub PAT, OAuth token, App credential, signing material, operator credential, capability or nonce in source, fixtures, logs, URLs, reports or chat. Anything seen in chat is treated as exposed: revoke and replace.
- **G-07** Do not invent endpoints, fields or error codes. The wire authority is `docs/gateway-contract.md` plus the schemas created in Stage 1.2; if silent, stop and report.
- **G-08** Never report a consequential operation as successful because a request was sent. Upload, accept, publish, reconcile are distinct states.
- **G-09** Replacing a placeholder removes its `SCAFFOLD PLACEHOLDER` line and `TODO(...)`. A task is not done while either remains in its files.
- **G-10** A failed gate blocks the phase transition. Record it; do not mark complete on documentation changes alone.
- **G-11** Update console docs, gateway docs and build-engine documentation together whenever an operation name, capability field, endpoint, release class or agent transport changes.

### A.4 Evidence-based baseline (5 Nov 2026)

Status words per the guide. "Per report" = taken from AI Studio agent reports; I did not execute tests.

| Area | Status | Evidence / gap |
|---|---|---|
| Contract, ADRs 0001–0010, conformance vectors | **implemented** | `docs/gateway-contract.md`, vectors reproducibility job in CI. Contract and code have drifted (see B.3) |
| Core utilities, codec, signers, minter | **implemented** (per report) | Stages 2.1–2.3 reports; no report exists for Stage 2.4 (transport) or Stage 3.1 (settings/session) although code is present: re-verify |
| Read layer and screens | **implemented** (per report) | Stages 3.2, 3.3 |
| Device key, enrolment, attestation (app side) | **implemented** (per report) | Stage 4.1 + 4.2 remediation; 216 JVM tests reported passing in the agent environment |
| Confirmation + mint single path | **implemented** (per report) | `ConfirmAndMintCapabilityUseCase`, `ArchitectureTest` guards `.mint(` |
| Connectivity remediation | **partial** | Only Stage 1 of 8 reported; its report lists the full Gradle run as unverified |
| Write use cases (publish, upload, reconcile, halt, resume, decide, work order, set status), `OperationRepositoryImpl`, publish/decision screens | **scaffold** | ~42 `TODO`/`SCAFFOLD` markers in 25 app files |
| Certificate pinning | **scaffold** | `CertificatePinning` throws `TODO`; pin set is commented out; `gateway.invalid` still in network config |
| Durable operation journal, background executor | **not started** | ADR 0009 chose encrypted Room + WorkManager; neither is in the build file |
| Lab (Phase 5) | **not started** | Only `LabEnvironmentGuard` exists |
| Phase 6 close-out (DEFERRED.md, device checklist, README) | **not started** | README still says "scaffold" |
| Gateway A | **partial, not deployed (unverified)** | ~2,560 lines. Implemented: config gate, `/health`, `/enroll*`, `/branches/:name/head`, status/plans reads, capability verify + nonce DO, device-key resolution, attestation verifier, enrolment registry DO, guards G1/G3/G4/G13, GitHub App auth, read handles. **Stubs:** guards G5–G8, G11, G14, G15; publish transaction; upload; reconcile; halt/resume; decide; sync; doctor; admin; audit record/emit/stream; PlanDO; ETag cache; rate coordinator; transaction; nonce cleanup alarm |
| Gateway CI | **permissive** | `cbridge-ci.yml`: `npm ci \|\| npm install`, `lint \|\| true`, `test:unit \|\| true`, `verify-log-hygiene \|\| true`; **no `gateway/package-lock.json`**; every gateway report says tsc/vitest "unverified in-agent" |
| Gateway deploy workflow | **scaffold** | `cbridge-deploy.yml`: `TODO: run V1–V12 against staging before production promote`; no approval gate |
| Android CI | **implemented, narrow** | JVM tests + debug build + vector reproducibility only; no release, signing or instrumented jobs |

**Two defects found during review that need action before anything else:**

1. `POST /tasks/:id/publish` on Gateway A returns **HTTP 200 `{ok:true}`** after the guards pass, with the message "publish transaction not yet implemented". A client reading the status code would report a publication that never happened. It must return 501 until the transaction exists (T1-103).
2. Gateway environment names are `test | production` only (`config/constants.ts`), but ADR 0010 and the build-engine plan require `test`, `staging`, `production`. Staging cannot currently be configured (BD-1.1).

### A.5 What I could not verify

- Whether Gateway A is deployed anywhere, and whether the `staging` GitHub environment holds Gateway A secrets (only build-engine's Gateways B/C staging deployment is evidenced).
- Any CI run result for `operator-console`; unit-test counts above are agent-reported.
- Cloudflare account state, the account `workers.dev` subdomain, GitHub App configuration.
- Whether the repository is intended to be public: both repositories were readable without credentials (BD-0.1).
- The Galaxy A03 Core's real biometric and attestation capabilities. Treat as unknown until Stage 7.2 reads the posture screen.

---

## Part B — Operator (HITL) responsibilities: verification, corrections, improvements

### B.1 Method and result

I checked the guide's 105 items and §10 observations against: the console README, ADRs, contract, capability format, the Kotlin and TypeScript source, CI workflows, agent reports, and the build-engine `STATUS.md`, plan and ADRs.

- **Consistent with the repositories and kept as written:** the credential model (§1.2, items 34–35); exclusions (items 13–14; manifest declares exactly `INTERNET` and `USE_BIOMETRIC`, `allowBackup=false`, `FLAG_SECURE` is applied in `MainActivity`; I found no `Runtime.exec`/`ProcessBuilder` in `app/src/main`); the Class A/B boundary and 0.5.0 Basic statements (§10.1 items 1, 3–5, confirmed against `docs/STATUS.md`); the build-engine plan-header conflict (§10.1 item 2, confirmed: `RELEASE-PLAN.md` on `main` still opens "VERSION 0.5.0 (unreleased)"); the README staleness (§10.2 item 6); the unverified connectivity report (item 7); the CI scope (item 8); the exclusions list (item 10).
- **Corrected** (B.2), **adjusted** (B.3), and **added** (B.4) items follow. Every other item is a process statement that this plan keeps and assigns to a phase (B.5).

### B.2 Corrections to the guide (publish as guide v2.1)

| # | Guide location | Problem | Correction |
|---|---|---|---|
| C-1 | §1.1 table, §10.2 item 12, §13 | States the gateway is "a separate project/repository, not part of the Android app tree". Gateway A **is** in this repository (`gateway/`), per the README ownership table and `V0.6.0-BUILD-PLAN.md`. Only Gateways B/C live in build-engine. | Gateway A = `operator-console/gateway`; deployed endpoint, secrets, runbook are this plan's Stage 1.6 outputs. Three gateways, three trust boundaries (A operator, B Class B read, C Class B write). |
| C-2 | §10.2 item 11 | Treats `cbridge-ci.yml` / `cbridge-deploy.yml` as "legacy/unclear". They are Gateway A's CI and deploy workflows under an old name, and both are permissive. | Rename to `gateway-ci.yml` / `gateway-deploy.yml`, make them fail-closed (Stage 1.1, 1.6). Until then: **do not deploy from them**. |
| C-3 | Items 51, 59–61, 67, §11 step 8 | Says biometric confirmation is mandatory and device unlock alone is not approval. The implemented and operator-approved design has **two confirmation tiers**: `hardware_confirm` (BiometricPrompt bound to the key; device PIN/pattern allowed on API 30+) and `in_app_confirm` (typed first 7 hex characters of the expected head SHA, no device lock). The operator requires that a device without biometrics must not stop the app. | Rewrite: *approval = the confirmation gesture the gateway's allowed tier requires, producing a signature with the device key*. The tier is recorded in the audit record. Which tier each operation needs, and whether production accepts `in_app_confirm`, is BD-3.1. Keep: never pre-mint, never approve a batch. |
| C-4 | Item 61 ("SHA truncated/ambiguous") | The `in_app_confirm` gesture types a 7-character prefix. | Clarify: the summary always shows the **full 40-character SHA**; the typed prefix is only the confirmation gesture. Approval is refused if the full SHA is not displayed. |
| C-5 | §1.2, item 6 vs. README "no persisted session token" | "Operator credential" is persisted (encrypted) while "session token" is not; the guide does not distinguish. | Define: operator bearer credential = persisted in Keystore-backed encrypted storage, gateway-scoped, rotatable; session = in-memory unlock only. |
| C-6 | Items 22–23, 71 | Name operations the console cannot perform today: `assume_done` preview, work-order creation, status mutation, `DECIDE`. Gateway contract v1 has endpoints only for publish, reconcile, upload, halt, resume, sync, doctor, status/plans/next, branch head, enrol. | Mark each as **not yet supported by the contract** until BD-1.3 closes and Stage 1.2 adds them; the guide must not describe them as available. |
| C-7 | Item 78, 79 vs. ADR 0010 | Guide recommends PR review and required checks. ADR 0010 deliberately keeps `main` unprotected so the AI Studio agent is never locked out. | Record as an **accepted residual risk** with compensating controls (BD-0.5): tag ruleset, release only from a CI-green tagged SHA, operator diff review since the last tag. |
| C-8 | Item 34, 1.2 last bullet vs. practice | The guide forbids credentials in agent conversations; session PATs have repeatedly been supplied in chat for this project. | Keep the prohibition. Add an operator task (OP-0.1): revoke every PAT previously pasted in chat and confirm. Stage-scoped PATs are issued only through a documented, expiring, single-repo path. |

### B.3 Adjustments (guide is right in principle; this project's facts differ)

| # | Guide item | Adjustment |
|---|---|---|
| A-1 | Items 3–5 (posture, StrongBox) | Add: the posture screen must show **which attestation security level and which auth tier** the gateway accepted. The acceptance device (Galaxy A03 Core, Android 13) is low-end; its real posture is unknown and must be read, not assumed (Stage 7.2). |
| A-2 | Items 9–10 (pinning, no weakening) | Pinning is not implemented yet and mTLS is deferred (ADR 0006/0007). Until Stage 2.3 passes, the guide's "certificate pinning" statements describe a **target**, not a current property. Debug-only cleartext to `10.0.2.2`/`localhost` is allowed in the debug overlay only. |
| A-3 | Item 11 (error distinction) | The app has `NOT_CONFIGURED` and `UNREACHABLE`; it does not yet separate TLS/pinning failure, authentication failure, stale state and policy refusal. Required by Stage 2.4. |
| A-4 | Item 12 (no cached authorization) | Cached reads are memory-only in 0.6.0 with visible data age (BD-4.1). |
| A-5 | Items 52, 64, 94 (unknown outcome) | Requires a gateway operation-lookup endpoint (BD-1.7) and a durable journal (Stage 3.2). Neither exists yet. |
| A-6 | Item 65 (distinct confirmation for halt/resume/decide) | Contract has `halt`/`resume`; `decide` is in the op enumeration with no endpoint. Per-operation tier minimums are BD-3.1. |
| A-7 | Items 41, 79–80 (gateway credential scope) | `gateway/src/adapter/github-app-auth.ts` exists. The credential model (GitHub App vs token, repository list, permissions) is undocumented: BD-0.6 and `docs/gateway-credential-scope.md` (T1-205). |
| A-8 | Items 8, 90 (revoke device key) | There is **no revocation route**: `routes/admin.ts` is a stub. Required in Stage 1.5; authority is BD-1.10. |
| A-9 | Item 102 (staging test list) | Gateway A has no V1–V12 gate in its deploy workflow and no live conformance identity (BD-1.9). |
| A-10 | Item 105 (local vs live conformance) | Kept. Stage 1.6 requires the live run to be archived separately from the local run. |

### B.4 Improvements added to the operator's responsibilities

1. **Per-environment keys.** ADR 0007 item 2 requires that a capability from one environment is not accepted by another. `ctxop-v1` has no environment field, so isolation must come from **a separate Keystore key per environment** (alias namespaced by environment and host) enrolled separately. Otherwise a capability captured on staging could be valid on production if branch, SHA and key coincide. Operator duty: never enrol the same device key in two environments. Implemented in T2-204.
2. **Lab is not an operator tool.** Lab signs with an HMAC secret and no confirmation. Operator duty: Lab only against a gateway reporting `environment: "test"`; the gateway must never hold a Lab HMAC key in staging or production (BD-1.6); the release APK must not contain Lab (BD-5.1).
3. **Pin rotation drill and expiry watch.** Android stops enforcing a pin set after its `expiration` date (the connection still succeeds on system CAs). Operator duty: record the expiry, rehearse rotation with the backup pin, and treat an expired pin set as an incident (BD-2.2).
4. **Release provenance with an unprotected `main`.** Operator duty: publish only from a tag created by the release workflow on a SHA whose CI run is green, after reviewing the diff since the previous tag.
5. **Credential rotation drills** for the operator bearer credential, the gateway's GitHub credential and the attestation root allow-list, each with a recorded failure-behaviour test (Stage 7.5).
6. **Third-party telemetry.** A reflection-based Firebase Crashlytics reporter exists (default disabled). Operator duty: none until BD-2.3 decides; the guide's "no payloads in third-party storage" rule applies.
7. **Operator audit of decisions.** Decision and halt/resume records carry attribution, rationale, scope and evidence (guide item 77) as required fields in the UI and the gateway audit record (Stages 3.4, 1.5).
8. **Evidence archive.** Each phase gate produces `docs/release/PHASE-<n>-EVIDENCE.md` (commands, run IDs, SHAs, outputs), reviewed by the operator before the next phase is authorized.

### B.5 Guide items → plan mapping (the items not changed above)

| Guide items | Where enforced / executed |
|---|---|
| 1–2 (official build, debug vs release) | Stage 7.1 release check; Stage 8.2 clean-device install |
| 3–8 (enrolment, posture, key loss) | Stages 2.4, 3.5; Stage 1.5 (revocation); Stage 7.2/7.5 drills |
| 13–14 (no shell, no commands) | `ArchitectureTest` extensions (T3-601) |
| 15–28 (plan inspection, readiness, work orders) | Stages 4.2, 4.3 (contract limits per C-6) |
| 29–40 (agent work, context) | Stages 6.1–6.3, with build-engine contracts |
| 41–58 (submission, publication) | Stages 1.3, 1.4, 3.3, 3.4, 6.2 |
| 59–67 (capability safety, halt/resume) | Stages 3.3–3.5 |
| 68–82 (review, plan-state integrity, governance) | Stages 1.4, 4.3, 4.4; repo controls BD-0.5 |
| 83–94 (blockers, incidents, recovery) | Stages 3.2, 4.3; drills in 7.3, 7.5 |
| 95–105 (release, deployment, lifecycle) | Phases 7, 8; cross-repo X-1…X-6 |
| §10.3 mandatory reconciliation list | See B.6 |

### B.6 Guide §10.3 "pre-operational reconciliation" → tasks

| §10.3 requirement | Task |
|---|---|
| Reconcile build-engine `RELEASE-PLAN.md` with v0.5.0 / 0.6.0 | X-1 (build-engine) |
| Single authoritative maturity/status document | T0-202 (`docs/STATUS.md`) |
| Identify gateway repository, endpoints, deployment commit, runbook | T1-601, T1-605 (this repo; corrects C-1) |
| Reconcile `cbridge-*` workflows | T1-102 (rename, harden) |
| Define the replacement point per Termux/local action | Stage 6.4, BD-6.3 |
| Prove live end-to-end operation | Stage 7.3 |
| Update docs together | Rule G-11 |

---

## Part C — Blocking decisions by phase

Each row blocks the stage named. "Default" is my recommendation, not a decision. **Closed** decisions from earlier ADRs are not reopened: C-1…C-5 (ADR 0007, 0008, 0009, 0010).

### Phase 0 — Baseline and hygiene

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-0.1 | Repository visibility. Both repositories are readable without credentials. Is public intended? | (a) public, keep deployment topology out of docs; (b) private | Confirm (a) and publish no secret or account-identifying value | 0.2 docs, 1.6 runbook |
| BD-0.2 | This file becomes the authoritative plan at repo root; `docs/V0.6.0-BUILD-PLAN.md` becomes historical | yes / no | yes | 0.2 |
| BD-0.3 | Android `applicationId`. Currently `com.inscopelabs.buildengine` (build-engine name, not the console's). Changing after first release creates a different app and requires re-enrolment. | keep / change now (e.g. `com.inscopelabs.operatorconsole`) | decide **now** | 0.2 version alignment, 7.1 |
| BD-0.4 | `public/assets/aistudio/*` (about 35 AI Studio upload copies, including files from another project such as `ClipInBoxApplication`) and `metadata.json` | delete assets / keep | delete assets after the agent confirms every file is superseded in `app/`; keep `metadata.json` unless AI Studio no longer needs it | 0.3 |
| BD-0.5 | Release provenance with an unprotected `main` (ADR 0010). Accept the residual risk in writing and adopt: `v*` tag ruleset (does not block pushes to `main`), release workflow only from a tag on a CI-green SHA, GitHub `release` environment with the operator as required reviewer | accept + controls / protect `main` (needs new decision, would block AI Studio) | accept + controls | 0.3, 7.1, 8.3 |
| BD-0.6 | Gateway GitHub credential model: GitHub App vs fine-grained token as a Worker secret; exact repository list and permissions; how Gateway A learns per-plan consumer repositories (build-engine DX-1) | App / token; static list / per-plan registry | GitHub App, least-privilege, per-environment installation, repository list in validated configuration | 1.3 |

### Phase 1 — Gateway A

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-1.1 | Environment names. Add `staging` to the gateway enum; where may lenient attestation run? | `test\|staging\|production`; lenient only on `test` | add `staging`; lenient only on `test`; Lab/HMAC only on `test` | 1.2 |
| BD-1.2 | Missing `ENVIRONMENT` on a Worker | invalid (fail closed) / default `test` | invalid; tests set it explicitly | 1.1 (router tests) |
| BD-1.3 | Operations beyond contract v1: `order`/work-order creation, `decide`, `set_in_progress`/`set_done`/`set`, `new`, `assume_done` preview | A: add endpoints for all; B: add `decide` + guarded status transitions only, work orders stay with engine tooling and the console shows a documented alternative; C: none | B, unless the build-engine BE-0 `CAPABILITY-CONFORMANCE.md` already defines them | 1.2, 1.4, 3.4, 4.2 |
| BD-1.4 | Plan-state mutation. Does Gateway A commit plan-state changes to the consumer repository? | gateway commits only through guarded status ops with expected-SHA compare-and-swap / engine tooling only | guarded ops with compare-and-swap; never hand-edit generated files | 1.4 |
| BD-1.5 | Guard identity. Contract fixtures use G1–G5 names; gateway/spec use G1–G15 | spec numbering authoritative; regenerate contract §5 | spec numbering authoritative | 1.2, 1.3 |
| BD-1.6 | HMAC "lab kid" on the gateway | accepted only when `ENVIRONMENT=test` and never configured elsewhere / accepted anywhere | test only | 1.2 |
| BD-1.7 | Operation lookup `GET /operations/{id}` keyed by idempotency key (needed for unknown outcomes and journal recovery) | add / not | add | 1.4, 3.2 |
| BD-1.8 | Where the durable audit record lives | DO write-ahead + commit to the consumer repo under a fixed audit path through the same guarded transaction (README: audit lives in the private consumer repository) / DO only | DO + consumer repo commit; define the path and schema | 1.5 |
| BD-1.9 | Live conformance identity. Staging requires strict attestation, so CI cannot enrol a software key there. | (a) deploy a throwaway `test` Worker for automated live conformance; (b) staging-only lenient enrolment of a CI key; (c) staging validated by the real device only | (a) + (c); never (b) | 1.6 |
| BD-1.10 | Device-key revocation and credential rotation authority | operator bearer + typed confirmation / capability from another enrolled device | bearer + typed confirmation, always audited | 1.5 |

### Phase 2 — Android configuration and transport

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-2.1 | Pin strategy on `workers.dev`. A **pin** is the fingerprint of a server public key built into the app; the app refuses any server presenting another key. A wrong pin locks every installed app out until a new APK is installed. Cloudflare manages the certificates, so leaf keys will change without notice. | (a) pin public keys of Cloudflare's intermediate/root CAs, at least two from different chains, primary + backup; (b) pin the leaf (breaks on Cloudflare rotation); (c) custom domain later | (a) | 2.3 |
| BD-2.2 | Pin-set expiry rule | release refused if expiry is earlier than release date + N days | N = 180, plus a CI warning at 60 days | 2.3, 7.1 |
| BD-2.3 | Crash reporting (Firebase Crashlytics reflection stub, default off) | remove for 0.6.0 / keep opt-in | remove | 2.4 |
| BD-2.4 | Unused Play Integrity dependency (deferred, ADR 0006) | remove from release build / keep | remove | 3.1 build PR |

### Phase 3 — Privileged operations and recovery

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-3.1 | Confirmation tier policy per environment and operation. The operator requirement: a missing biometric must not stop the app. | (a) staging and production accept both tiers, gateway records the tier, `decide`/`resume` require `hardware_confirm` when the device can provide it; (b) production hardware-only (a no-biometric device can read but not act); (c) both tiers for every operation | (a) | 3.3–3.5, 1.5 |
| BD-3.2 | Journal encryption and content | SQLCipher-style encrypted Room / Room with Keystore-wrapped field encryption; store metadata + idempotency key only | store **no signed capability, signature or nonce** after send; recover by lookup (BD-1.7); mechanism: Room + Keystore AES-GCM field encryption (fewer native dependencies) | 3.1 |
| BD-3.3 | Authorization of the build-file PR (Room, WorkManager, encryption, manifest/proguard changes, removal of Play Integrity) | authorize / not | authorize as one PR by Claude, operator merges | 3.2 |

### Phase 4 — Operator interface

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-4.1 | Offline cache | memory-only with visible age / persisted plan cache | memory-only; writes disabled when offline or stale | 4.1 |
| BD-4.2 | Minimum supported screen/profile for acceptance | Galaxy A03 Core only / plus emulator profile | A03 Core + one emulator | 4.5 |

### Phase 5 — Lab

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-5.1 | Lab packaging | excluded from release (debug/`lab` source set) / included with runtime guard | **excluded from release**; release check fails if Lab or the HMAC signer is in the release build | 5.5, 7.1 |
| BD-5.2 | Lab target gateway | local `wrangler dev` only (ADR 0010: `test` is local) / deployed `test` Worker | deployed `test` Worker on its own hostname (needed for BD-1.9a); LAN cleartext only in debug | 5.3 |

### Phase 6 — Class B operator workflow and cbridge coverage

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-6.1 | How the console reaches Gateways B/C (links, revoke, audit) | via Gateway A as the only console-facing surface / direct with per-gateway credentials | via Gateway A (one credential on the device) | 6.1 |
| BD-6.2 | Authority for revocation of read links and write sessions | bearer + typed confirmation (protective, audited) / new `ctxop` operations | bearer + typed confirmation; no new ops (avoids vector and cross-repo changes) | 6.3 |
| BD-6.3 | Termux retirement scope, row by row (`verify`, `bootstrap`, `init`, local git, shell stay out of the app) | operator signs each coverage-matrix row | per ADR 0008 | 6.4, 8.4 |

### Phase 7 — CI, release integrity, acceptance

| ID | Decision | Options | Default | Blocks |
|---|---|---|---|---|
| BD-7.1 | Release signing key custody and distribution | (a) keystore as GitHub `release`-environment secret, APK from GitHub Release, sideload; (b) Play internal testing with Play App Signing; (c) both | (a) for 0.6.0; operator generates and backs up the key offline; the key is never given to an agent | 7.1 |
| BD-7.2 | Severity-1 definition for "zero open" (build-engine item 50) | list | Sev-1 = any bypass of capability/guard/halt, any false success report, any credential or capability leak, any data loss on the journal | 7.3, 7.4 |
| BD-7.3 | Gateway A deployment identity: Worker names, hostnames (fixed at first deployment), GitHub `staging`/`production` environments with required reviewer for production | operator supplies | record in `docs/ENVIRONMENTS.md` (no secret values) | 1.6 |
| BD-7.4 | Acceptance device window | dates | operator schedules | 7.2 |

### Phase 8 — Release

| ID | Decision | Default | Blocks |
|---|---|---|---|
| BD-8.1 | Release-note wording (build-engine ADR 0006 item 4 conditional wording) | final wording only after Phase 7 evidence; status vocabulary | 8.1 |
| BD-8.2 | Publication authorization at tag time (G-12) | operator | 8.3 |
| BD-8.3 | Termux retirement signature | operator, per command | 8.4 |

### Cross-repository dependencies (build-engine)

| ID | Dependency | Needed by |
|---|---|---|
| X-1 | Reconcile build-engine `RELEASE-PLAN.md` header and phase state with v0.5.0 released and 0.6.0 in progress | Phase 0 |
| X-2 | BE-0 documents: `COMPATIBILITY-MATRIX.md`, `CAPABILITY-CONFORMANCE.md`, `CBRIDGE-COVERAGE-MATRIX.md` (not present on `main` at review) | Stage 1.2, 6.4 |
| X-3 | Protocol identifier `build-engine/0.6` change | Stage 8.1 |
| X-4 | DX-1 credential model for per-plan repositories; DX-2 hostnames and pilot repositories | Stage 1.3, 1.6 |
| X-5 | Production-on-`workers.dev` gate change in build-engine | Stage 7.3 (production only) |
| X-6 | BE-7 joint pilot scheduled with this plan's Stage 7.4; BE-8 compatibility record lists console APK and Gateway A with checksums | Phase 7–8 |

---

## Part D — Phases and stages

Task IDs: `T<phase>-<n>`; the hundreds digit is the stage (T3-204 = stage 3.2). Prefix with the plan number when registered. Evidence: each stage report `agent-reports/<UTC>-<slug>.md`; each phase gate adds `docs/release/PHASE-<n>-EVIDENCE.md`.

Path shorthand: `M/` = `app/src/main/kotlin/com/inscopelabs/buildengine/`, `T/` = `app/src/test/kotlin/com/inscopelabs/buildengine/`, `A/` = `app/src/androidTest/kotlin/com/inscopelabs/buildengine/`, `G/` = `gateway/`.

### Phase 0 — Baseline, decisions, hygiene (docs and housekeeping only)

**Gate 0:** BD-0.1…0.6 closed; one authoritative status document; no stale plan competing with this one.

**Stage 0.1 — Decision record `[CLAUDE]` `[OP]`**
- T0-101 Claude drafts ADR 0011 (this plan's closed decisions) from Part C answers; operator merges.
- T0-102 OP-0.1: revoke every PAT ever pasted in chat; confirm in writing.
- T0-103 OP: apply the tag ruleset and `release` environment per BD-0.5.

**Stage 0.2 — Documentation baseline `[AIS]` paths: `README.md`, `docs/**`**
- T0-201 Rewrite `README.md`: real status in the guide's vocabulary, correct toolchain table (Compose BOM: `libs.versions.toml` says `2026.06.01`, README says `2026.09.00`; one agent report says `compileSdk 34`, the build says 36), remove "scaffold" claim only for items that are implemented.
- T0-202 Create `docs/STATUS.md`: one table of every phase/stage with implemented / tested / merged to `main` / released / safe to use (guide §10.3).
- T0-203 Mark `docs/V0.6.0-BUILD-PLAN.md` and `docs/console-connectivity-remediation.md` "superseded by RELEASE-PLAN.md".
- T0-204 Fix the duplicate ADR number: two files are numbered 0007. Keep `0007-production-transport-posture.md`; rename `0007-confirmation-tiers-and-attestation-challenge.md` to ADR 0012 and fix every reference (ADR 0006, `device-enrollment.md`, `threat-model.md`, `gateway-contract.md`, reports are left unchanged).
- T0-205 Amend ADR 0004: environments are `test`, `staging`, `production`; Lab only on `test` (BD-1.1/1.6).
- T0-206 Commit the corrected guide as `docs/operator-guide.md` (v2.1) implementing B.2–B.4.
- T0-207 Decide version fields per BD-0.3 (docs only; the build change goes in Stage 7.1).

**Stage 0.3 — Hygiene `[AIS]` for deletions, `[CLAUDE]` for workflows**
- T0-301 Delete `public/assets/aistudio/*` per BD-0.4 after listing each file's replacement in the report.
- T0-302 `[BE]` X-1: build-engine reconciles its `RELEASE-PLAN.md` header.

### Phase 1 — Gateway A: verifiable, contract-true, fail-closed

**Gate 1:** Gateway A passes typecheck, lint, unit, conformance, log-hygiene and bundle checks in CI with **no permissive step**; every advertised operation is implemented and refuses unsafe input; a deliberately failing gate demonstrably blocks deployment; live conformance on deployed staging is archived.

**Stage 1.1 — Make the gateway verifiable `[CLAUDE]` for workflows/lockfile, `[AIS]` for fixes**
- T1-101 Commit `gateway/package-lock.json`; `npm ci` only; add `typecheck`.
- T1-102 Rename to `gateway-ci.yml` / `gateway-deploy.yml`; remove every `|| true`; fix `setup-node` cache path; add a job that runs the existing unit suite.
- T1-103 **Fail-closed stubs:** `POST /tasks/:id/publish` and the stub routes (`admin`, `decisions`, `doctor`, `sync`) return 501 `NOT_IMPLEMENTED` through the error catalogue. No stub returns 2xx.
- T1-104 Run the suite once in CI; fix what breaks. Known risks from reports: router tests omitting `ENVIRONMENT` (BD-1.2), X.509 test helpers, unverified `tsc`.
- T1-105 Demonstrate that a failing test and a failing log-hygiene check each turn CI red (record the run).

**Stage 1.2 — Contract and schema authority `[AIS]` paths `docs/**`, `G/docs/**`, `G/src/errors/**`**
- T1-201 Reconcile `docs/gateway-contract.md`, `docs/capability-format.md`, `G/docs/*` with the code and with BD-1.1…1.5, and with build-engine `CAPABILITY-CONFORMANCE.md` (X-2). Specific drifts: error body field (`code` in the contract, `error` in the gateway: **align the gateway to the contract**); operation names (`PUBLISH` in the wire example, lower-case closed enum in code); contract lists 5 capability ops while the enum has 13; contract §5 guard IDs vs. spec G1–G15; contract §2 mapping lacks status transition, decision, order and operation lookup.
- T1-202 Publish JSON Schemas for every request/response; add a CI check that fails when Kotlin DTOs or gateway types diverge from them (demonstrate with a deliberate mismatch).
- T1-203 Regenerate vectors for any wire change; keep the reproducibility job green.
- T1-204 Add endpoints per BD-1.3/1.7 to the contract (status transition, decide, operations lookup, admin revoke/list, audit read).
- T1-205 `docs/gateway-credential-scope.md`: the GitHub credential, its permissions, repositories, and blast radius (guide items 41, 79–80).

**Stage 1.3 — Guards and the publish transaction `[AIS]` `G/src/guards/**`, `G/src/adapter/**`, `G/src/durable-objects/**`**
- T1-301 Implement G5 (ancestry), G6 (non-empty delta), G7 (no merge commits), G8 (denied paths), G11 (secret patterns), G14 (halt), G15 (submission immutable); register in the publish guard set; fixtures per contract §5.
- T1-302 Implement `adapter/transaction.ts` (branch-head compare-and-swap on the expected SHA), `etag-cache.ts`, `rate-coordinator.ts`, `errors.ts`.
- T1-303 Real publish: capability gate → guards → transaction → audit write-ahead → response with `auditRef`; replay and stale head produce `NONCE_REPLAYED` / `HEAD_DRIFT` and no write.
- T1-304 Nonce store: schedule the alarm cleanup at expiry (open TODO in `nonce-store.ts`); test that cleanup never frees an unexpired nonce.
- T1-305 `[OP]` Provision the test consumer repository with the fixture branches (`task-fx-*`); no credential enters the repository.

**Stage 1.4 — Remaining operations `[AIS]`**
- T1-401 `upload` (size/count caps, safe paths, single `SUBMISSION.json`, id and plan match; mirrors `cbridge`).
- T1-402 `reconcile` (reports `MERGED:` / `CLOSED:` outcomes; never merges on its own).
- T1-403 `halt` / `resume`: repository-wide state in a Durable Object plus marker; G14 reads it; `resume` separately scoped (guide item 66).
- T1-404 `decide`, status transitions (per BD-1.3/1.4): attribution, rationale, scope and evidence are required fields; plan-state changes through compare-and-swap only.
- T1-405 `sync`, `doctor` (reports rulesets and Actions permissions state read-only; flags the accepted R0-004 residuals).
- T1-406 `GET /operations/{id}` (BD-1.7): returns `unknown | in_progress | succeeded | refused | conflict`; idempotency key stored before the side effect.

**Stage 1.5 — Audit, privacy, administration `[AIS]`**
- T1-501 `audit/record.ts`, `emit.ts`, `durable-objects/audit-stream.ts` per BD-1.8; fields include operation, plan, task/order, branch, expected and actual SHA, `kid`, confirmation tier, outcome, request id; never capability, signature, nonce, payload or credential.
- T1-502 Extend `verify-log-hygiene` to scan for capability, nonce, signature and token shapes in code and in captured test logs.
- T1-503 `routes/admin.ts`: list enrolments, revoke a `kid`, rotate-in-place checks (BD-1.10). A revoked key is refused immediately and audited.
- T1-504 Finalize `docs/error-catalogue.md`; every code has a test.

**Stage 1.6 — Conformance and deployed staging acceptance `[CLAUDE]` `[OP]`**
- T1-601 `docs/ENVIRONMENTS.md` and runbook (deploy, rollback, rotate credential, revoke device, halt/resume, incident) with no secret values (BD-7.3).
- T1-602 `[CLAUDE]` Deploy workflow as a fail-closed sequence: typecheck → unit → conformance V1–V12 → privacy/log hygiene → bundle verification → staging acceptance → **production approval by required reviewer**.
- T1-603 `[OP]` Create `staging` GitHub environment and secrets (Cloudflare token and account, GitHub App credentials, operator bearer credential, attestation root fingerprints `ATTESTATION_ROOT_SHA256`, signing keys); production environment with required reviewer. Separate values per environment.
- T1-604 Deploy a throwaway `test` Worker and run live conformance (BD-1.9a); deploy staging Gateway A; archive both outputs separately from the local run (guide item 105).
- T1-605 Record deployment version, commit SHA, workflow run, environment, conformance result, rollback point (guide item 104).

### Phase 2 — Android: configuration, transport, connectivity

**Gate 2:** a fresh install with no configuration shows "Not configured" and makes zero network calls; with staging configured it reports preflight results per check; pins match the deployed host; no `gateway.invalid` remains.

**Stage 2.1 — Complete the connectivity remediation (stages 2–8 of the old plan) `[AIS]`**
- T2-101 No network in ViewModel `init` (dashboard, plans): `Idle`/`NotConnected` until refresh. Test: zero repository calls when not configured.
- T2-102 Finish `GatewayBaseUrlStore` (still has `TODO`) and inject into `GatewayHttpClient`; Settings edit/save/clear with validation (HTTPS in release; no credentials in URL; hostname rules; debug cleartext only to `10.0.2.2`/`localhost`).
- T2-103 Explicit "Test connection" → `/health`; show environment, version, time; persist last snapshot.
- T2-104 Shell status banner (`Not configured | Unreachable | Connected (test|staging|production)`), deep link to Settings.
- T2-105 Redacting gateway activity log (method, path, outcome only) and diagnostics panel.
- T2-106 Timeouts, no redirects, response-size cap; `NotConfiguredUiTest` and `ErrorMapperTest` regression gates; re-run the full Gradle suite in CI (the Stage 1 report left it unverified).

**Stage 2.2 — Environment safety `[AIS]`**
- T2-201 The configured expected environment must equal the gateway's reported environment; otherwise all privileged actions are disabled with an explicit message.
- T2-202 Per-environment operator credential slots; switching environment never reuses another environment's credential.
- T2-203 Settings never display or log the credential; copy/paste into prompts is not offered.
- T2-204 **Per-environment device key:** alias namespaced by environment and host; enrolment records per environment; a key enrolled for staging is never offered for production (B.4 item 1). Tests for alias derivation and refusal.

**Stage 2.3 — Pinning `[AIS]` code, `[CLAUDE]` for `network_security_config.xml` and CI**
- T2-301 `[OP]` Capture the SPKI pins for the deployed staging host chain (Claude supplies the exact commands; no shell commands go to the agent).
- T2-302 Implement `CertificatePinning` and the OkHttp pinner: primary + backup, rotation without app update where the design allows; refuse when pins are empty in release.
- T2-303 `[CLAUDE]` `network_security_config.xml`: real domains and pin-set with expiry per BD-2.2; remove `gateway.invalid`.
- T2-304 `docs/PIN-ROTATION.md` and a rotation rehearsal checklist.
- T2-305 JVM tests: match, mismatch, backup match, expired set; `CertificatePinningTest` (instrumented) implemented.

**Stage 2.4 — Preflight and failure vocabulary `[AIS]`**
- T2-401 Preflight screen with independent checks: configured, reachable, TLS/pin, authentication, clock skew (gateway tolerance 10 s), contract-version compatibility, enrolment state, posture and accepted tier.
- T2-402 Extend failure states: add `TLS_PINNING`, `AUTH`, `STALE`, `POLICY_REFUSED` alongside `NOT_CONFIGURED`, `UNREACHABLE` (guide item 11).
- T2-403 Remove Crashlytics reflection reporter and diagnostic hooks per BD-2.3; keep local redacted diagnostics only.

### Phase 3 — Android: privileged operations and durable recovery

**Gate 3:** publish, upload, reconcile, halt, resume, decide and status transition work against the controlled gateway; crash between send and acknowledgement never duplicates or falsely reports an operation; no scaffold marker in these classes.

**Stage 3.1 — Build-file authorization `[CLAUDE]` PR, operator merges (BD-3.3)**
- T3-101 Add Room, WorkManager and the encryption approach chosen in BD-3.2; remove Play Integrity per BD-2.4; proguard rules; manifest only if required (WorkManager needs none for non-expedited work).

**Stage 3.2 — Journal, executor and outcome model `[AIS]`**
- T3-201 Outcome model: `Succeeded | Refused | Conflict | Timeout | UncertainCompletion | RecoveryRequired` as a sealed type across the repository boundary.
- T3-202 Journal schema (intent, idempotency key, state, timestamps, `auditRef`; **no capability, signature or nonce**); intent written **before** send.
- T3-203 Restart recovery: on launch, uncertain operations are resolved by `GET /operations/{id}`; the app never resends automatically. WorkManager performs lookups only.
- T3-204 Repeated taps are idempotent at the use-case level.
- T3-205 Tests: crash between journal write and send, between send and ack, process death, network loss, replay refusal, head drift.

**Stage 3.3 — Publish (old T4-301/T4-302) `[AIS]`**
- T3-301 `PublishTaskUseCase`, `PublishViewModel`, `PublishConfirmScreen` (operation, plan, task/order, branch, **full 40-character head**, TTL countdown from approval, posture and tier badge, guard summary), `PublishProgressScreen`, `OperationResultBanner` (shows `auditRef`).
- T3-302 Success is reported only after gateway confirmation **and** a live-head check; otherwise `UncertainCompletion`.
- T3-303 Real tests for each distinct refusal (scope refused, replayed, head drifted, halt active), declined confirmation, TTL expiry while the prompt is open.

**Stage 3.4 — Remaining operations (old Stage 4.4) `[AIS]`**
- T3-401 `HaltUseCase` / `ResumeUseCase` (own scope; resume requires the recorded cause).
- T3-402 `ReconcileUseCase` (shows `MERGED:`/`CLOSED:` outcome; no merge by the app).
- T3-403 `UploadSubmissionUseCase`: system document picker (no storage permission), size cap, on-device SHA-256, client pre-checks mirroring `cbridge`, original preserved.
- T3-404 `DecideFrameworkUseCase` + `ui/decision` with attribution, rationale, scope, evidence fields; `SetTaskStatusUseCase` per BD-1.3/1.4; `CreateWorkOrderUseCase` only if BD-1.3 says so, otherwise a documented alternative screen.
- T3-405 `sync` / `doctor` on the dashboard.

**Stage 3.5 — Confirmation tier hardening `[AIS]`**
- T3-501 Enforce the per-operation minimum tier from BD-3.1; the summary shows the tier and why.
- T3-502 No-biometric device path tested end to end (typed prefix, never a silent downgrade of an operation that requires `hardware_confirm`).
- T3-503 Key invalidation (biometric enrolment change, lock removal) → `NeedsReenrollment` flow with gateway-side revocation of the old `kid`.

**Stage 3.6 — Architecture tests `[AIS]`**
- T3-601 Extend `ArchitectureTest`: no GitHub token patterns anywhere in source or resources; no `Runtime.exec`/`ProcessBuilder`; `.mint(` only in the single path; no network call from a write use case that bypasses the journal; `lab` not imported by production packages.

### Phase 4 — Operator interface completion

**Gate 4:** every screen listed below exists with state tests; no scaffold marker or theme placeholder remains in `ui/`.

**Stage 4.1 — Shell and dashboard `[AIS]`**
- T4-101 Dashboard and navigation; data age and provenance on every cached read; state-changing actions disabled when offline or stale (BD-4.1); halt state always visible.

**Stage 4.2 — Plans and tasks `[AIS]`**
- T4-201 Plan discovery/inspection; task status/next; dependencies, gates, acceptance criteria, **executor classification** (agent vs operator-only). Only what the contract exposes (C-6); anything else shows "not supported by this release".

**Stage 4.3 — Intake and review `[AIS]`**
- T4-301 Submission inspection (paths, file hashes, expected branch SHA, restrictions); blocked-task views that keep **missing input, spec conflict, decision needed, policy refusal, technical failure** distinct (guide item 37); rework creates a new revision identity and preserves the original.

**Stage 4.4 — Governance and audit views `[AIS]`**
- T4-401 Decision history, audit inspection, device list/revoke screens (using T1-503), halt/resume screen.

**Stage 4.5 — Polish `[AIS]`**
- T4-501 Replace the placeholder theme files (`Color.kt`, `Theme.kt`, `Type.kt` still carry scaffold markers); accessibility and small-screen checks per BD-4.2; delete every remaining `SCAFFOLD` marker not on the deferred list.

### Phase 5 — Lab (old IMPLEMENTATION-PLAN Phase 5 and Stage 6.1)

**Gate 5:** "Run all" executes the scenario catalogue against the deployed `test` Worker and produces a report; a deliberately weakened gateway fails the right scenarios; the release build contains no Lab code.

**Stage 5.1 — Scenario engine `[AIS]`:** T5-101 `lab/Scenario`, `ScenarioResult`, `ScenarioRunner` (sequential, cancellable, no retries). T5-102 `LabReport` (JSON, in memory, shared by explicit action only; no signature, nonce or credential).
**Stage 5.2 — Mutations `[AIS]`:** T5-201 `Mutator` with tests proving each mutation changes only what it claims. T5-202 `ScenarioCatalogue` from the contract error registry plus fixtures; a unit test asserts every error code has at least one scenario (including `ENROLL_*`, `AUTH_TIER_NOT_ALLOWED`, `CONFIG_INVALID`).
**Stage 5.3 — Lab UI and safety `[AIS]`:** T5-301 `LabScreen`/`LabViewModel`. T5-302 `HmacCapabilitySigner` only inside Lab; persistent banner "LAB — not an operator approval". T5-303 environment guard before each scenario (already present; wire it). T5-304 Lab toggle off by default, never persisted.
**Stage 5.4 — Contract self-check `[AIS]`:** T5-401 `ContractCheck` flags unknown error codes and DTO drift.
**Stage 5.5 — Packaging and deferred list `[CLAUDE]` + `[AIS]`:** T5-501 `[CLAUDE]` build change per BD-5.1. T5-502 `[AIS]` `docs/DEFERRED.md` listing every remaining scaffold item with the ADR that defers it (mTLS stub, Play Integrity). T5-503 `[AIS]` implement or delete remaining instrumented-test placeholders; the rest go to the device checklist.

### Phase 6 — Class B operator workflow and cbridge coverage

**Gate 6:** the coverage matrix has no empty row; every native feature has a named test; negative tests pass against the controlled gateways.

**Stage 6.1 — Context links `[AIS]`, depends on X-2/BD-6.1:** T6-101 choose exact files, bind to an immutable commit and work order, issue links through Gateway A; list status, re-mint, revoke.
**Stage 6.2 — Submission approval and transfer `[AIS]`:** T6-201 inspection of hashes, expected branch SHA, path restrictions, conflict reporting; transfer and publication remain explicit operator actions through the Phase 3 single path.
**Stage 6.3 — Revocation and audit across gateways `[AIS]`:** T6-301 per BD-6.1/6.2; tests for tampered hash, moved head, path escape, revoked link.
**Stage 6.4 — cbridge coverage matrix `[AIS]` docs + tests:** T6-401 mirror build-engine `CBRIDGE-COVERAGE-MATRIX.md`; every command → native use case or documented alternative; `README` scope text matches; `[OP]` signs each row (BD-6.3).

### Phase 7 — CI, release integrity, acceptance

**Gate 7:** release APK built, signed and verified by CI from a tagged commit; release check green; A03 Core acceptance passed; end-to-end and recovery evidence archived; Sev-1 count zero for advertised scope.

**Stage 7.1 — CI and release machinery `[CLAUDE]` (protected files)**
- T7-101 Jobs: JVM tests, instrumented tests on an emulator, release assembly, APK signing (BD-7.1), `apksigner verify`, checksum, dependency/vulnerability scan, vector reproducibility, schema/DTO check.
- T7-102 **Release check** that fails on: any `TODO(`/`SCAFFOLD`, `gateway.invalid` or dummy URL, empty or expired-within-N-days pin set, cleartext allowed, Lab or HMAC signer in the release build, Play Integrity or Crashlytics classes, a debug keystore, unresolved `OPERATOR`/`GITHUB` token patterns, unsupported operation paths.
- T7-103 Version source of truth: `build.gradle.kts` currently hard-codes `0.1.0`/code 1 while `build-apk-debug.yml` reads a `version.properties` that does not exist; consolidate to one mechanism, set 0.6.0.
- T7-104 Consolidate `build-apk-debug.yml` and `android-ci.yml`; remove `continue-on-error` on lint or document why.

**Stage 7.2 — Device acceptance `[OP]` with checklist from `[AIS]` (`docs/DEVICE-TEST-CHECKLIST.md`)**
- T7-201 Galaxy A03 Core (Android 13): read posture (security level, tier), enrol, publish, process termination mid-operation, background restrictions, with and without device lock/biometrics, key invalidation, `FLAG_SECURE`, airplane mode and recovery, pin failure, clock skew.

**Stage 7.3 — Live end-to-end proof `[OP]` + `[CLAUDE]` harness**
- T7-301 One archived run of the guide's chain on staging with a disposable repository: enrolment → read status/plan → operator confirmation → capability verification → guarded gateway action → GitHub effect → audit record → recovery/reconciliation.
- T7-302 Failure matrix: timeout after send, lost response with consumed nonce, replay, head drift, halt, gateway outage, revoked key, expired pin set, halt during an operation.

**Stage 7.4 — Joint pilot (build-engine BE-7, X-6) `[OP]`**
- T7-401 App against staging Gateways A, B and C with a real Class B agent: retrieval, submission, review, sealing, transfer, publication, revocation, audit inspection.

**Stage 7.5 — Security drills `[OP]` + `[AIS]` docs**
- T7-501 Update `docs/threat-model.md` (R-list, incl. `in_app_confirm`, shared zone, pin lockout, unprotected `main`).
- T7-502 Drills with recorded results: operator credential rotation, gateway GitHub credential rotation and failure behaviour, device loss and re-enrolment, pin rotation, log scan for secrets.

### Phase 8 — Release candidate, publication, retirement

**Gate 8:** the operator authorizes publication; the compatibility record links engine archive, console APK and the three gateways with versions and checksums.

**Stage 8.1 — Release candidate `[AIS]` docs, `[CLAUDE]` version files:** T8-101 version 0.6.0 in app and gateway; `CHANGELOG`; `docs/STATUS.md` final; guide v2.1 final; release notes in the status vocabulary (BD-8.1); protocol identifier per X-3; `COMPATIBILITY` entry for BE-8.
**Stage 8.2 — Independent verification `[OP]`:** T8-201 clean-device install from the release asset, verify checksum and signature, upgrade and recovery path, no unverified APK used as an authority client.
**Stage 8.3 — Publication `[OP]`:** T8-301 tag created by the workflow after explicit operator authorization (BD-8.2); no agent publishes.
**Stage 8.4 — Termux retirement `[OP]`:** T8-401 operator signs the coverage matrix per command (BD-6.3); until then the Termux route remains available for any unsigned row.
**Stage 8.5 — Post-release record `[AIS]`:** T8-501 version, commit SHA, artifact checksum, workflow run, environment, conformance result, rollback point (guide item 104).

**Non-blocking backlog (old OC-9):** unified operation history, persisted read cache, submission comparison viewer, native notifications, APK update notification, structured diagnostic export (no credentials). Do not delay Phase 8.

---

## Part E — Operator action register

| ID | When | Action |
|---|---|---|
| OP-0.1 | Phase 0 | Revoke all previously pasted PATs; confirm |
| OP-0.2 | Phase 0 | Apply `v*` tag ruleset and `release` environment (BD-0.5) |
| OP-0.3 | Phase 0 | Answer Part C Phase 0–1 decisions; merge ADR 0011 |
| OP-1.1 | Stage 1.3 | Provision test consumer repository with `task-fx-*` branches |
| OP-1.2 | Stage 1.6 | Create GitHub App; create `staging`/`production` environments; supply secrets (never in chat) |
| OP-1.3 | Stage 1.6 | Supply attestation root fingerprints and per-environment signing keys |
| OP-2.1 | Stage 2.3 | Run the pin-capture commands against the deployed host; confirm backup pin |
| OP-3.1 | Stage 3.1 | Authorize and merge the build-file PR |
| OP-7.1 | Stage 7.1 | Generate and back up the release signing key offline; install as environment secret |
| OP-7.2 | Stage 7.2 | Run the device checklist on the A03 Core; archive results |
| OP-7.3 | Stage 7.3–7.4 | Run end-to-end and pilot evidence; review and sign |
| OP-8.1 | Phase 8 | Authorize publication; sign Termux retirement rows |

## Part F — Traceability of unfinished work from earlier plans

| Source | Unfinished item | Stage |
|---|---|---|
| IMPLEMENTATION-PLAN | T4-301, T4-302 (publish) | 3.3 |
| IMPLEMENTATION-PLAN | T4-401…T4-405 (halt, resume, reconcile, upload, status, work order, decide, sync, doctor) | 3.4 |
| IMPLEMENTATION-PLAN | Phase 5 (T5-101…T5-401) | 5.1–5.4 |
| IMPLEMENTATION-PLAN | T6-101 (sweep, `DEFERRED.md`), T6-102 (instrumented placeholders) | 4.5, 5.5 |
| IMPLEMENTATION-PLAN | T6-201 (device checklist), T6-202 (README), T6-203 (architecture doc `lab` row) | 7.2, 0.2, 5.5 |
| IMPLEMENTATION-PLAN | Decision P4 (BOM drift) | 0.2 |
| IMPLEMENTATION-PLAN | Missing reports for Stages 2.4 and 3.1 | 0.2 (status table marks "re-verify") |
| V0.6.0-BUILD-PLAN | OC-0 (schemas, DTO divergence check, matrix mirror) | 1.2, 6.4 |
| V0.6.0-BUILD-PLAN | OC-1 (Gateway A real, lockfile, no `\|\| true`) | 1.1–1.5 |
| V0.6.0-BUILD-PLAN | OC-2 (config, pins, preflight) | 2.1–2.4 |
| V0.6.0-BUILD-PLAN | OC-3 (enrolment lifecycle; app side done per report) | Gate 2/3 verification, 3.5 |
| V0.6.0-BUILD-PLAN | OC-4 (journal, outcome model, write use cases) | 3.1–3.4 |
| V0.6.0-BUILD-PLAN | OC-5 (operator interface) | Phase 4 |
| V0.6.0-BUILD-PLAN | OC-6, OC-7 | Phase 6 |
| V0.6.0-BUILD-PLAN | OC-8 (CI, release, A03 Core, cross-system) | Phase 7 |
| V0.6.0-BUILD-PLAN | OC-9 | Non-blocking backlog |
| Connectivity remediation | Stages 2–8 | 2.1 |
| gateway-repair-ga reports | Missing `ENVIRONMENT` default question | BD-1.2 |
| gateway-repair-ga reports | Contract `code` vs gateway `error` body | T1-201 |
| gateway-repair-ga reports | Attestation `pathLen` and `keyUsage` interpretation against real Android chains; operator-supplied root fingerprints | 7.2 (real chain), OP-1.3 |
| gateway-repair-ga reports | GW-6 conformance suites, halt/sync/doctor, transaction layer, GW-7 | 1.3–1.6 |
| gateway-repair-ga reports | All "tsc/vitest unverified in-agent" | 1.1, G-04 |
| ADR 0007 (transport) | Pins and rotation procedure; release refused without them | 2.3, 7.1 |
| ADR 0007 (tiers) / ADR 0012 after rename | Gateway tier policy per environment | BD-3.1, 3.5 |
| ADR 0009 | Encrypted Room + WorkManager | 3.1–3.2 |
| ADR 0010 | Runtime hostname validation; production host only after build-engine gate change | 2.2, X-5 |
| ADR 0008 | Task-state transition operation; coverage matrix; Termux retirement | 1.4, 6.4, 8.4 |
| Stage 4.2 report | Android-side tier handling done; gateway-side policy and docs to confirm | 1.5, 3.5 |
| `cbridge-ci.yml` / `cbridge-deploy.yml` | `TODO` gates, V1–V12 before production | 1.1, 1.6 |
| `public/assets/aistudio/*` | Stray upload copies | 0.3 |

## Part G — Release gates summary

| Gate | Exit criteria |
|---|---|
| 0 | BD-0.* closed; STATUS document; competing plans marked superseded |
| 1 | Gateway A fully implemented, CI hard gates, live conformance archived on `test` and staging |
| 2 | Configuration, pinning and preflight proven on the deployed host |
| 3 | Write operations and crash-safe recovery proven against the controlled gateway |
| 4 | Operator interface complete, no placeholders in `ui/` |
| 5 | Lab passes against `test`, absent from release |
| 6 | Coverage matrix complete and signed per row by the operator at Gate 8 |
| 7 | Release check green, device acceptance, live E2E and joint pilot evidence, Sev-1 zero |
| 8 | Operator authorizes publication; compatibility record complete; Termux retirement signed per command |

## Part H — Risks

| Risk | Mitigation |
|---|---|
| AI Studio silently overwrites out-of-band changes | One stage per run; Claude rebases open branches first; diff review; protected paths |
| Gateway work "done" without ever running | Lockfile and hard CI first (Stage 1.1); stages close only on green CI |
| False success report from a stub | T1-103 now; G-08; success only after head verification |
| Pin lockout or silent pin expiry | BD-2.1/2.2, rotation drill, release check on expiry |
| Cross-environment capability replay | Per-environment keys (T2-204), per-environment credentials, environment equality check |
| Lab code or HMAC signer reaching a release | BD-5.1, release check |
| Credential exposure in chat | OP-0.1, G-06 |
| Contract drift between app, gateway and build-engine | Schemas and DTO check in CI; X-2; G-11 |
| Low-resource device behaviour found late | A03 Core run is Stage 7.2, but Stage 2.4 and 3.2 each include an on-device smoke run by the operator |
| Unprotected `main` | BD-0.5 compensating controls; tag-based releases only |
