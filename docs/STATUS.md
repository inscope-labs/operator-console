# Operator Console — Status Matrix

**Authoritative project status document per Guide §10.3 and `RELEASE-PLAN.md» (Stage 0.2 / T0-202).**  
Last updated: **2026-11-14 (PR3-001 Phase 3 baseline reconciliation; HEAD SHA unverified — no `.git` in sandbox)**.  
Reference plan of record: [`RELEASE-PLAN.md»](../RELEASE-PLAN.md).

---

## 1. Status Vocabulary (Guide §1.3)

| Term | Strict Definition |
|---|---|
| **implemented** | Code exists, adheres to contract, wire format, and rules; verified by executed tests. |
| **reference only** | Document, schema, or vector intended as architectural guidance or specification; not executable runtime code. |
| **scaffold** | Code compiles and structure exists, but carries `TODO», placeholder logic, or mock returns. Unsafe for operational use. |
| **not started** | Required item for v0.6.0 whose code, tests, or documentation have not yet been authored. |
| **not deployed** | Code exists and passes local verification, but has not been deployed to an active Cloudflare Worker or test environment. |
| **unsupported** | Out of scope by architectural design (e.g., shell execution, on-device git worktree, broad storage access). |

> **Critical Safety Constraint**: Nothing in this repository may be described as "production ready, deployed, and supported" until the gates in Phase 7 have passed with archived evidence in `docs/release/PHASE-7-EVIDENCE.md».

---

## 2. Complete Stage-by-Stage Status Table

| Phase / Stage | Area / Component | Implemented | Tested | Merged to main | Released | Safe to use | Notes / Evidence |
|---|---|---|---|---|---|---|---|
| **Phase 0** | **Baseline, Decisions & Hygiene** | | | | | | Gate 0 open |
| Stage 0.1 | Decisions Record (ADR 0011, OP-0.1, BD-0.5) | partially | no | yes | no | **no** | BD-0.1–0.6 confirmed; ADR 0011 drafting assigned to Claude |
| Stage 0.2 | Documentation Baseline | **implemented** | yes | yes | no | **yes** | README updated, STATUS.md created, ADR 0012 renumbered, Guide v2.1 published |
| Stage 0.3 | Hygiene & Asset Cleanup | **implemented** | yes | yes | no | **yes** | Deleted 35 redundant files in `public/assets/aistudio/*» after mapping replacements |
| **Phase 1** | **Gateway A (Cloudflare Worker)** | | | | | | Gate 1 open |
| Stage 1.1 | Fix Publish & Stubs 501 (T1-103) | **implemented** | pending CI | yes | no | **yes (fail-closed)** | Publish and stub routes return 501 NOT_IMPLEMENTED, zero 2xx on stubs, unit tests added |
| Stage 1.2 | Environment Model (BD-1.1) | **implemented** | pending CI | yes | no | **yes** | Environments closed to test/staging/production; lenient mode and HMAC refused outside test |
| Stage 1.3 | Contract Schemas & Error Catalogue Freeze (T1-201, ADR 0003) | **implemented** | pending CI | yes | no | **yes** | 22 JSON schemas published; code/error aligned; frozen error catalogue & spec G1–G15 numbering |
| Stage 1.4 | CI Hardening (Fail-Closed) & Lockfile (T1-101, T1-102) | not started | no | no | no | **no** | Package lockfile, removal of `|| true», workflow rename to gateway-*.yml |
| Stage 1.5 | Phase 1 Completion Report & Sign-off | not started | no | no | no | **no** | Gate 1 completion report (Appendix A template) |
| **Phase 2** | **Android Transport & Connectivity** | | | | | | Gate 2 open |
| Stage 2.1 | Connectivity Remediation (BaseUrlStore, no init dial, timeouts, redirects, response cap) | **implemented** | yes | yes | no | **yes** | Stages 2–8 implemented: idle init, BaseUrlStore injection & URL validation, health snapshot persistence, staging banner, redacted activity log, redirect refusal, response size cap; 302 unit tests passing |
| Stage 2.2 | Environment Safety (per-env keys & slots) | **implemented** | yes | yes | no | **yes** | T2-201–T2-204 implemented: expected environment matching, environment mismatch gate, per-env credential slots, credential hygiene, canonical alias derivation (op_<env>_<host>), per-env key enrolment isolation & refusal; 316 unit tests passing |
| Stage 2.3 | Certificate Pinning & Expiry Rules | **implemented** | yes | yes | no | **yes** | T2-302, T2-304, T2-305 implemented: CertificatePinning with primary/backup pins, BD-2.2 180-day expiry enforcement, release empty pin refusal, dynamic rotation without app update, OkHttp pinner & interceptor, docs/PIN-ROTATION.md, 17 pinning JVM tests passing (333 total) |
| Stage 2.4 | Preflight Screen & Failure Vocabulary | **implemented** | yes | yes | no | **yes** | T2-401 (PreflightScreen with 8 independent checks: configured, reachable, TLS/pins, auth, clock skew <=10s, contract version, enrolment, posture/tier), T2-402 (extended failure states TLS_PINNING, AUTH, STALE, POLICY_REFUSED in ErrorMapper), T2-403 (Crashlytics reflection reporter & diagnostic hooks removed per BD-2.3; local redacted diagnostics preserved); 349 unit tests passing |
| **Phase 3** | **Privileged Operations & Durable Recovery** | | | | | | Gate 3 open; baseline reconciled in `docs/release/PHASE-3-BASELINE-INVENTORY.md` |
| Stage 3.1 | Build-file Authorization (Room, WorkManager) | **implemented in code; not accepted** | JVM yes (`476e447`, `3e795dd`); R8 release/runtime unverified | yes (see note on `7fdd304`) | no | **no** | `T3-101` / `PR3-002`, BD-2.4, BD-3.2, BD-3.3: Room `2.8.5` (KSP schema `1.json`), WorkManager `2.11.2`, platform AES-GCM, Play Integrity absent (`BuildConfigStage31Test` green in `476e447`/`3e795dd`). **OPEN:** commit `7fdd304` edited `app/proguard-rules.pro` (a protected path) outside a `[CLAUDE]` PR; operator acceptance is OPEN (`PR3-002`, ADR 0014). |
| Stage 3.2 | Operation Journal & `GET /operations/{id}` Recovery | **implemented** | JVM yes (`476e447`, `3e795dd`; fakes/`MockWebServer`); Android runtime/integration **no** | yes | no | **no (JVM fakes only)** | `T3-201`–`T3-205` (`PR3-010`–`PR3-015`): `OperationOutcome`, `OperationJournalDatabase`, `AndroidKeystoreJournalCipher`, `OperationRecoveryWorker`, `OperationRepositoryImpl` write-before-send & lookup (`OperationJournalExecutorTest`, `OperationLookupWireTest`, `IdempotencyKeyGeneratorTest` green at `476e447`/`3e795dd`). `[REG+]` GCM AAD (`PR3-010`) **implemented**: every encrypted field is bound to its journal record and column through GCM associated data (`JournalAssociatedData`, `JournalGcm`, `JournalFieldCipherAssociatedDataTest`, PR #71). Android Keystore/Room/WorkManager runtime (`PR3-040`/`PR3-041`) and live/controlled Gateway A (`PR3-042`) unverified. |
| Stage 3.3 | Guarded Publish Flow (full 40-char SHA confirmation) | **implemented in code; not accepted** | JVM pass at `476e447`/`3e795dd` (`PublishTaskUseCaseTest`, `PublishBannerPresenterTest`); closeout/VM & `androidTest` CI result unverified | PR #66 in workspace; post-PR #67 CI unverified | no | **no** | `T3-301`–`T3-303` (`PR3-020`, `PR3-021`): PR #66 + closeout tests (`PublishViewModelTest`, `androidTest/ui/publish/*`; `androidTest` runtime never run in CI). **OPEN (1):** Publish cannot run end to end: the capability `order` identifier has no source in the gateway contract or the Task model; `PublishOrderResolver` fails closed. Resolution per ADR 0014 D1 (contract revision + gateway change) is pending. **OPEN (2):** T3-303 'TTL expiry while the prompt is open' is removed from Gate 3 by ADR 0014 D4; replaced by 'a minted capability expires exactly the configured lifetime after approval'. |
| Stage 3.4 | Remaining Operations (halt, resume, upload, reconcile, decide, status, sync/doctor) | **implemented in code; not accepted** | **no (`79bde91` CI FAILED on `main` compile; post-sync CI result unverified)** | `main` had `79bde91` compile fail; post-sync CI unverified | no | **no** | `T3-401`–`T3-405` (`PR3-022`–`PR3-027`): **0** Phase 3 placeholders remain (`10` `app/src/main/` files carry markers: `8` Phase 4, `2` Phase 5). CI `79bde91` FAILED missing `GatewayEndpoints.planDecide`; synced locally (`GatewayEndpoints.planDecide` + Stage 3.4 tests + `DecisionScreenTest`), **CI result unverified**. **ADR 0014 D2:** `decide` is a documented reduction (`decision` + `notes`, rationale in `notes`, attribution via enrolled `kid` in audit log, no separate `evidence` wire field; current `DecideFrameworkUseCase` code still defaults pre-D2 `contractSchemaSettled = false`). **ADR 0014 D3:** gateway agent adds `POST /plans/{id}/status` (`plans.ts` currently registers `GET` only). **ADR 0014 D5:** BD-1.3 option B (`CreateWorkOrderUseCase` `UnsupportedByContract`, not an operational path). |
| Stage 3.5 | Confirmation Tier Hardening (degraded mode) | **implemented in code; not accepted** | **no (`75bb8e9` CI FAILED test compile; `0ec3923`–`1651db9` cancelled; post-sync CI result unverified)** | `75bb8e9` failed, `0ec3923`–`1651db9` cancelled; post-sync CI unverified | no | **no** | `T3-501`–`T3-503` (`PR3-030`–`PR3-032`). **ADR 0014 D5:** BD-3.1 option (a) confirmed (`DECIDE`/`RESUME` require `hardware_confirm` when available; explicit degraded tier otherwise). `ConfirmationTierPolicy`, Step 3b enforcement, `RevokeDeviceKeyUseCase`, `DeviceEnrollmentStore.retire`, and 3 JVM test classes exist, **with zero green CI runs** (`75bb8e9` failed missing `FakeGatewayApi.revokeKey`; `0ec3923`–`1651db9` cancelled; synced `FakeGatewayApi.revokeKey` has **CI result unverified**). No-biometric runtime (`PR3-031`), Keystore invalidation instrumentation (`PR3-032`), and live `POST /admin/revoke` unverified; `NeedsReenrollment` UI deferred to Stage 4.4. |
| Stage 3.6 | Architecture Tests (single mint path, secrets, shell, journal, lab) | **implemented in code; not accepted** | JVM local yes (`ArchitectureTest` 7 tests, `ArchitectureScannerTest` 12 tests; full `:app:testDebugUnitTest` 481/481 across 69 suites, 0 skipped/failed); CI result unverified until Claude confirms the pushed SHA | workspace unverified (no `.git` in sandbox); pending push/CI | no | **no** | `T3-601` (`PR3-033`): Added Rules A (tokens/keys), B (shell/Git), D (journal bypass; enrollment `enroll`/`rotateKey`/`revokeKey`/`enrollChallenge` excluded), E (lab/HMAC isolation with ratchet baseline), F1 (`Logger` interpolation), F2 (Room `@Entity` properties) while preserving original `.mint(` test unchanged; backed by `ArchitectureScanner` + 12 fixture tests in `ArchitectureScannerTest`. **Rule E ratchet baseline findings (findings, NOT approvals; operator decision pending):** `capability/signing/CapabilitySigner.kt` (1 `HmacCapabilitySigner` KDoc ref), `di/CryptoModule.kt` (1 `HmacCapabilitySigner` KDoc ref), `ui/lab/LabScreen.kt` (2 `com.inscopelabs.buildengine.lab` imports), `ui/lab/LabViewModel.kt` (6 `com.inscopelabs.buildengine.lab` imports). Stage 3.6 NOT closed until Claude confirms green CI on pushed SHA. |
| **Phase 4** | **Operator Interface Completion** | | | | | | Gate 4 open |
| Stage 4.1 | Shell & Dashboard (data age, halt visibility) | partially | yes | yes | no | **no** | Dashboard exists; data age and offline write-blocking needed |
| Stage 4.2 | Plans & Tasks (discovery, executor classification) | partially | yes | yes | no | **no** | Read screens exist; executor classification pending |
| Stage 4.3 | Intake & Review (submission inspection, blockers) | not started | no | no | no | **no** | Multi-failure blocker categorization pending |
| Stage 4.4 | Governance & Audit Views (decision history, revoke) | not started | no | no | no | **no** | Decision history and key revocation UI pending |
| Stage 4.5 | Theme & Accessibility Polish | scaffold | partially | yes | no | **no** | `Theme.kt», `Color.kt», `Type.kt» carry scaffold placeholders |
| **Phase 5** | **Lab Engine** | | | | | | Gate 5 open |
| Stage 5.1 | Scenario Engine (runner, memory-only reports) | not started | no | no | no | **no** | `LabEnvironmentGuard» exists; runner not implemented |
| Stage 5.2 | Mutations & Error Coverage Catalogue | not started | no | no | no | **no** | Mutators not implemented |
| Stage 5.3 | Lab UI & Safety Banner | not started | no | no | no | **no** | Persistent warning banner and test-only gate needed |
| Stage 5.4 | Contract Self-check | not started | no | no | no | **no** | DTO drift checker not implemented |
| Stage 5.5 | Release Packaging Isolation (exclude Lab) | not started | no | no | no | **no** | Lab source set separation deferred to Stage 5.5 / 7.1 |
| **Phase 6** | **Class B Operator Workflow & cbridge Coverage** | | | | | | Gate 6 open |
| Stage 6.1 | Context Links (Gateway A issuance, status, revoke) | not started | no | no | no | **no** | Depends on build-engine BE-0 documents |
| Stage 6.2 | Submission Approval & Transfer | not started | no | no | no | **no** | Depends on Phase 3 single-path confirmation |
| Stage 6.3 | Cross-Gateway Revocation & Audit Linkage | not started | no | no | no | **no** | Cross-gateway linkage through Gateway A |
| Stage 6.4 | cbridge Coverage Matrix (row-by-row sign-off) | not started | no | no | no | **no** | Termux retirement blocked on row-by-row sign-off |
| **Phase 7** | **CI, Release Integrity & Acceptance** | | | | | | Gate 7 open |
| Stage 7.1 | CI Hardening & Release Check | scaffold | unverified | yes | no | **no** | Permissive `|| true» in gateway CI; release check needed |
| Stage 7.2 | Device Acceptance (Samsung Galaxy A03 Core) | not started | no | no | no | **no** | Physical device acceptance checklist pending |
| Stage 7.3 | Live End-to-End Staging Proof | not started | no | no | no | **no** | Full loop with disposable repository pending |
| Stage 7.4 | Joint Pilot with Build Engine (BE-7) | not started | no | no | no | **no** | Dependent on Gateways A, B, and C staging |
| Stage 7.5 | Security Drills (credential & key rotation) | not started | no | no | no | **no** | Key loss, credential revocation drills pending |
| **Phase 8** | **Release Candidate, Publication & Retirement** | | | | | | Gate 8 open |
| Stage 8.1 | Release Candidate 0.6.0 | not started | no | no | no | **no** | Version alignment, CHANGELOG, protocol identifier |
| Stage 8.2 | Independent Clean-Device Verification | not started | no | no | no | **no** | Clean sideload install and checksum verification |
| Stage 8.3 | Tagged Release Publication | not started | no | no | no | **no** | Gated on Operator manual authorization |
| Stage 8.4 | Termux Retirement Per Command | unsupported | no | no | no | **no** | Termux remains fallback until each row is signed |
| Stage 8.5 | Post-Release Compatibility Record | not started | no | no | no | **no** | Compatibility entry in build-engine record |

## Phase 3 detail

Per-task baseline reconciliation as of `PR3-001` (`2026-11-14`; `HEAD` SHA `unverified` — no `.git` directory in sandbox; see `docs/release/PHASE-3-BASELINE-INVENTORY.md` for full file paths and CI facts). Gateway completion reports (`docs/release/PHASE-*-COMPLETION-REPORT.md`) are Gateway A evidence only and are not counted below.

| Task / Stage (`PR3`) | Scope | Implemented in code | JVM-tested (test class + CI SHA, or "CI unverified") | Android-runtime-tested | Integration-tested | Operator-accepted |
|---|---|---|---|---|---|---|
| `T3-101` / 3.1 (`PR3-002`) | Build deps, schema, manifest, ProGuard | **yes** (`7fdd304` edited protected `proguard-rules.pro`) | `BuildConfigStage31Test` (`476e447`, `3e795dd`) | no (R8 release & device runtime unverified) | no (n/a) | **no** (`7fdd304` `proguard-rules.pro` review/acceptance OPEN) |
| `T3-201` / 3.2 (`PR3-010`) | `OperationOutcome` sealed model | **yes** | `OperationJournalExecutorTest` (`476e447`, `3e795dd`) | no | no | no |
| `T3-202` / 3.2 (`PR3-010`, `PR3-011`) | Encrypted Room journal, write-before-send | **yes** (`[REG+]` GCM AAD implemented in PR #71: ciphertext bound to its record and column) | `OperationJournalExecutorTest`, `IdempotencyKeyGeneratorTest` (`476e447`, `3e795dd`; fake cipher/DAO), `JournalFieldCipherAssociatedDataTest` (PR #71) | no (`PR3-040` `[REG+]` Room/Keystore tests absent) | no | no |
| `T3-203` / 3.2 (`PR3-014`) | Launch `OperationRecoveryWorker` lookups only | **yes** | `OperationJournalExecutorTest`, `OperationLookupWireTest` (`476e447`, `3e795dd`) | no (`PR3-041` `[REG+]` WorkManager tests absent) | no (`PR3-042`) | no |
| `T3-204` / 3.2 (`PR3-012`) | Repeated-tap idempotency & intent fingerprint | **yes** | `IdempotencyKeyGeneratorTest`, `OperationJournalExecutorTest` (`476e447`, `3e795dd`) | no | no (`PR3-042`) | no |
| `T3-205` / 3.2 (`PR3-013`, `PR3-015`) | Crash/network/replay/drift matrix | **yes** (`PR3-013` unified ack/lookup path & monotonicity in `OperationRepositoryImpl`; `PR3-015` fault matrix open) | JVM local yes (`OperationJournalExecutorTest` 37/37, `OperationLookupWireTest` 4/4, `VerifyBranchHeadUseCaseTest` 6/6, `Stage34OperationRepositoryTest` 4/4 — CI unverified on `PR3-013` edits) | no | no (`PR3-042`) | no |
| `T3-301` / 3.3 (`PR3-020`, `PR3-021`) | Guarded Publish flow & full 40-char SHA UI | **yes** (`PublishOrderResolver` fails closed pending ADR 0014 D1) | `PublishTaskUseCaseTest`, `PublishBannerPresenterTest`, `ConfirmAndMintCapabilityUseCaseTest` (`476e447`/`3e795dd`); `PublishViewModelTest` (CI unverified) | no (`androidTest/ui/publish/*` never run in CI) | no (blocked on D1 + `PR3-042`) | no |
| `T3-302` / 3.3 (`PR3-013`, `PR3-021`) | Gateway ack + live-head verification | **yes** (`PR3-013` shared `verifyAndFinalizeCompletedOperation` path, scope/ID/40-char SHA/monotonicity; `PR3-003` Q2/Q3 traceability matrix open) | JVM local yes (`OperationJournalExecutorTest` 37/37, `PublishTaskUseCaseTest` 8/8, `PublishViewModelTest` 10/10, `Stage34OperationRepositoryTest` 4/4 — CI unverified on `PR3-013` edits) | no | no (`PR3-042`) | no |
| `T3-303` / 3.3 (`PR3-021`) | Refusal, declined, capability lifetime tests | **partial** (ADR 0014 D4 replacement test & `RELEASE-PLAN.md` edit pending) | `PublishTaskUseCaseTest`, `PublishBannerPresenterTest` (`476e447`/`3e795dd`); `PublishViewModelTest` (CI unverified) | no (`PublishConfirmScreenTest`, `PublishProgressScreenTest`, `PublishFlowTest` never run in CI) | no | no |
| `T3-401` / 3.4 (`PR3-024`) | `HaltUseCase`, `ResumeUseCase` + recorded cause | **yes** | `HaltAndResumeUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified — `79bde91` failed; post-sync unverified) | no | no (`PR3-042`) | no |
| `T3-402` / 3.4 (`PR3-023`) | `ReconcileUseCase` (`MERGED:`/`CLOSED:`) | **yes** | `ReconcileUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified) | no | no (`PR3-042`) | no |
| `T3-403` / 3.4 (`PR3-022`) | `UploadSubmissionUseCase` + SAF picker + `cbridge` checks | **yes** | `UploadSubmissionUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified) | no (SAF `OpenDocument` runtime unverified) | no (`PR3-042`) | no |
| `T3-404` / 3.4 (`PR3-025`, `PR3-026`, `PR3-027`) | `Decide`, `SetTaskStatus`, `CreateWorkOrder` deferral | **partial** (`DecideFrameworkUseCase` still defaults pre-D2 `ContractBlocked`; `SetTaskStatus` lacks gateway `POST` route per D3; `CreateWorkOrderUseCase` deferred per D5) | `DecideFrameworkUseCaseAndViewModelTest`, `SetTaskStatusUseCaseTest`, `CreateWorkOrderUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified — `79bde91` failed missing `planDecide`; post-sync unverified) | no (`DecisionScreenTest` never run in CI) | no (D3 route missing + `PR3-042`) | no (policy decisions D2/D3/D5 accepted; code/tests unaccepted) |
| `T3-405` / 3.4 (`—`) | Dashboard `sync` / `doctor` | **yes** | `DashboardViewModelTest` (CI unverified) | no | no | no |
| `T3-501` / 3.5 (`PR3-030`) | Per-op min auth tier & summary reason | **yes** | `ConfirmationTierPolicyTest`, `ConfirmAndMintTierEnforcementTest` (CI unverified — `75bb8e9` failed, `0ec3923`–`1651db9` cancelled) | no | no | no (BD-3.1(a) D5 confirmed; code/tests unaccepted) |
| `T3-502` / 3.5 (`PR3-031`) | No-biometric typed prefix path, no silent downgrade | **yes** | `ConfirmationTierPolicyTest`, `ConfirmAndMintTierEnforcementTest`, `PublishViewModelTest` (CI unverified) | no (device/emulator `PR3-031` record absent) | no | no |
| `T3-503` / 3.5 (`PR3-032`) | Key invalidation, `NeedsReenrollment`, gateway revoke | **partial** (`NeedsReenrollment` UI deferred to Stage 4.4) | `RevokeDeviceKeyUseCaseTest`, `DeviceEnrollmentStoreTest`, `ConfirmAndMintCapabilityUseCaseTest` (CI unverified — `75bb8e9` failed missing `FakeGatewayApi.revokeKey`; post-sync unverified) | no (crypto `androidTest` files are `SCAFFOLD PLACEHOLDER`) | no (live `/admin/revoke` unverified) | no |
| `T3-601` / 3.6 (`PR3-033`) | `ArchitectureTest` (5 invariants) | **partial (1 of 5 checks only)** | `ArchitectureTest` (`.mint(` check only green at `476e447`/`3e795dd`; 4 checks missing) | no (n/a) | no (n/a) | no |

---

## 3. Deliberately Unsupported Features (By Design)

The following capabilities are excluded from the Operator Console architecture. Any PR or proposal introducing them will be rejected:

- **On-device GitHub credentials**: No PAT, OAuth token, or GitHub App secret on Android.
- **Local shell / Termux bridge**: No `Runtime.exec()», `ProcessBuilder», or local terminal wrappers.
- **Repository or branch creation**: `bootstrap» and `init» belong to CI or external setup tools.
- **Verification execution on phone**: `check», `render --check», and test execution run exclusively on CI runners.
- **Local git worktrees**: The phone operates solely via minted HTTP capabilities sent to Gateway A.
- **Persistent session tokens**: Sessions exist in-memory only; capability TTL (5 min) is the outer boundary.
- **Unbounded cloud analytics**: Third-party crash trackers with remote payload storage (e.g. Firebase Crashlytics) are removed in v0.6.0.
