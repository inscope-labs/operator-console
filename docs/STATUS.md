# Operator Console — Status Matrix

**Authoritative project status document per Guide §10.3 and `RELEASE-PLAN.md» (Stage 0.2 / T0-202).**  
Last updated: **2026-11-14 (PR3-001 Phase 3 baseline reconciliation; HEAD SHA unverified — no `.git` in sandbox)**. **2026-11-16:** the Manual Pipeline module detail was added below ("Manual Pipeline module detail") for PR #75 — module repaired, 121 JVM tests added, CI green on `44b5022`; not merged, not operator-accepted. **2026-11-16:** the Plan Management Authoring (PMA) detail was added below ("Plan Management Authoring (PMA) detail") for PR #76 — plan authoring interface, 69 JVM tests added, CI green on `ea3a5fb`; not merged, not operator-accepted.  
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
| Stage 3.2 | Operation Journal & `GET /operations/{id}` Recovery | **implemented (PR3-012 findings F1–F4 open)** | JVM local yes (`OperationJournalExecutorTest` 45/45, `FaultMatrixTest` 9/9, `IdempotencyAndFingerprintTest` 9/9, `OperationJournalStateTransitionTest` 3/3, `JournalFieldCipherAssociatedDataTest` 17/17, `OperationLookupWireTest` 4/4, `IdempotencyKeyGeneratorTest` 4/4; 557/557 `:app:testDebugUnitTest`; CI unverified on `PR3-011`/`PR3-012`/`PR3-015`); Android runtime/integration **no** | workspace unverified (no `.git`); pending push/CI | no | **no (JVM fakes & MockWebServer only)** | `T3-201`–`T3-205` (`PR3-010`–`PR3-015`): `OperationOutcome`, `OperationJournalDatabase`, `AndroidKeystoreJournalCipher`, `OperationRecoveryWorker`, `OperationRepositoryImpl` write-before-send & lookup. `[REG+]` GCM AAD (`PR3-010`) implemented in PR #71. `PR3-011` (`T3-202`) atomic `commitIntentIfNoActive` transaction, CAS `transitionState`, pre-dispatch DB verification, 8x8 forward-only state machine, and concurrent batch coalescing implemented (`OperationJournalDao_Impl` KSP-compiled SQLite transaction + CAS verified; DAO/executor tests run with `InMemoryOperationJournalDao` / `FakeOperationJournalDao`). `PR3-012` (`T3-204`) audit + 9 gap tests in `IdempotencyAndFingerprintTest` (`ACKNOWLEDGED` reuse, 10-field & boundary sensitivity, `409 IDEMPOTENCY_KEY_REUSED`, multi-restart key retention, CSPRNG/wire nonce vs key separation); **findings F1–F4 recorded unpatched on `main`** (F1 `decide` omits `notes` from `OperationIntent`, F2 local key collision returns `RecoveryRequired` instead of `Conflict`, F3 `ErrorMapper.GATEWAY_CODES`, F4 unchecked fingerprint vs decrypted fields). `PR3-015` (`T3-205`) 14-scenario fault matrix recorded in `docs/release/PHASE-3-FAULT-MATRIX.md` with gap coverage in `FaultMatrixTest` (scenarios 1, 4, 5, 7, 11, 13 via `MockWebServer` & JVM `JournalGcm`). Android Keystore/Room/WorkManager runtime (`PR3-040`/`PR3-041`) and live/controlled Gateway A (`PR3-042`) unverified (**NOT MET**). |
| Stage 3.3 | Guarded Publish Flow (full 40-char SHA confirmation) | **implemented in code; not accepted** | JVM pass at `476e447`/`3e795dd` (`PublishTaskUseCaseTest`, `PublishBannerPresenterTest`); closeout/VM & `androidTest` CI result unverified | PR #66 in workspace; post-PR #67 CI unverified | no | **no** | `T3-301`–`T3-303` (`PR3-020`, `PR3-021`): PR #66 + closeout tests (`PublishViewModelTest`, `androidTest/ui/publish/*`; `androidTest` runtime never run in CI). **OPEN (1):** Publish cannot run end to end: the capability `order` identifier has no source in the gateway contract or the Task model; `PublishOrderResolver` fails closed. Resolution per ADR 0014 D1 (contract revision + gateway change) is pending. **OPEN (2):** T3-303 'TTL expiry while the prompt is open' is removed from Gate 3 by ADR 0014 D4; replaced by 'a minted capability expires exactly the configured lifetime after approval'. |
| Stage 3.4 | Remaining Operations (halt, resume, upload, reconcile, decide, status, sync/doctor) | **implemented in code; not accepted** | **no (`79bde91` CI FAILED on `main` compile; post-sync CI result unverified)** | `main` had `79bde91` compile fail; post-sync CI unverified | no | **no** | `T3-401`–`T3-405` (`PR3-022`–`PR3-027`): **0** Phase 3 placeholders remain (`10` `app/src/main/` files carry markers: `8` Phase 4, `2` Phase 5). CI `79bde91` FAILED missing `GatewayEndpoints.planDecide`; synced locally (`GatewayEndpoints.planDecide` + Stage 3.4 tests + `DecisionScreenTest`), **CI result unverified**. **ADR 0014 D2:** `decide` is a documented reduction (`decision` + `notes`, rationale in `notes`, attribution via enrolled `kid` in audit log, no separate `evidence` wire field; current `DecideFrameworkUseCase` code still defaults pre-D2 `contractSchemaSettled = false`). **ADR 0014 D3:** gateway agent adds `POST /plans/{id}/status` (`plans.ts` currently registers `GET` only). **ADR 0014 D5:** BD-1.3 option B (`CreateWorkOrderUseCase` `UnsupportedByContract`, not an operational path). **`PR3-027` (PR #73):** the deferral is now structural, not conventional — `CreateWorkOrderUseCase` is out of the Hilt graph and no production source references it, the gateway surface exposes no work-order creation route (I-10), and the plan detail screen shows the documented alternative (contract §3.6, revision 1.2: work orders are created by engine tooling); the card offers no action. |
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
| `T3-202` / 3.2 (`PR3-010`, `PR3-011`) | Encrypted Room journal, atomic intent-before-dispatch, validated state machine | **yes** (`PR3-010` GCM AAD implemented in PR #71; `PR3-011` atomic DAO `@Transaction` + CAS `transitionState` + `OperationJournalState` forward-only validation) | JVM local yes (`OperationJournalExecutorTest` 45/45, `OperationJournalStateTransitionTest` 3/3, `IdempotencyKeyGeneratorTest` 4/4, `JournalFieldCipherAssociatedDataTest` 17/17; fake cipher/in-memory DAO; CI unverified on `PR3-011` edits) | no (`PR3-040` `[REG+]` Room SQLite/Keystore androidTest absent) | no | no |
| `T3-203` / 3.2 (`PR3-014`) | Launch `OperationRecoveryWorker` lookups only | **yes** | `OperationJournalExecutorTest`, `OperationLookupWireTest` (`476e447`, `3e795dd`) | no (`PR3-041` `[REG+]` WorkManager tests absent) | no (`PR3-042`) | no |
| `T3-204` / 3.2 (`PR3-012`) | Repeated-tap idempotency & intent fingerprint | **partial** (audited; findings **F1** `decide` omits `notes` from `OperationIntent` & **F2** local key collision returns `RecoveryRequired` instead of `Conflict` recorded unpatched on `main` per rule; see `agent-reports/2026-11-16T17-15-00Z-pr3-012-idempotency-audit.md`) | JVM local yes (`IdempotencyAndFingerprintTest` 9/9, `IdempotencyKeyGeneratorTest` 4/4, `DeviceNonceSourceTest` 6/6, `OperationJournalExecutorTest` 45/45, `OperationJournalStateTransitionTest` 3/3 — CI unverified on `PR3-012` edits) | no (`PR3-040` **NOT MET**) | no (`PR3-042` **NOT MET**) | no |
| `T3-205` / 3.2 (`PR3-013`, `PR3-015`) | Crash/network/replay/drift matrix | **yes (JVM ceiling)** (`PR3-013` unified ack/lookup path & monotonicity in `OperationRepositoryImpl`; `PR3-015` 14-scenario matrix in `docs/release/PHASE-3-FAULT-MATRIX.md` + `FaultMatrixTest`; Android runtime/WorkManager/gateway `NOT MET`) | JVM local yes (`OperationJournalExecutorTest` 45/45, `FaultMatrixTest` 9/9, `OperationJournalStateTransitionTest` 3/3, `JournalFieldCipherAssociatedDataTest` 17/17, `OperationLookupWireTest` 4/4, `VerifyBranchHeadUseCaseTest` 6/6, `PublishViewModelTest` 10/10, `Stage34OperationRepositoryTest` 4/4 — CI unverified on `PR3-013`/`PR3-015` edits) | no (`PR3-040`/`PR3-041` **NOT MET**) | no (`PR3-042` **NOT MET**) | no |
| `T3-301` / 3.3 (`PR3-020`, `PR3-021`) | Guarded Publish flow & full 40-char SHA UI | **yes** (`PublishOrderResolver` fails closed pending ADR 0014 D1) | `PublishTaskUseCaseTest`, `PublishBannerPresenterTest`, `ConfirmAndMintCapabilityUseCaseTest` (`476e447`/`3e795dd`); `PublishViewModelTest` (CI unverified) | no (`androidTest/ui/publish/*` never run in CI) | no (blocked on D1 + `PR3-042`) | no |
| `T3-302` / 3.3 (`PR3-013`, `PR3-021`) | Gateway ack + live-head verification | **yes** (`PR3-013` shared `verifyAndFinalizeCompletedOperation` path, scope/ID/40-char SHA/monotonicity; `PR3-003` Q2/Q3 traceability matrix open) | JVM local yes (`OperationJournalExecutorTest` 37/37, `PublishTaskUseCaseTest` 8/8, `PublishViewModelTest` 10/10, `Stage34OperationRepositoryTest` 4/4 — CI unverified on `PR3-013` edits) | no | no (`PR3-042`) | no |
| `T3-303` / 3.3 (`PR3-021`) | Refusal, declined, capability lifetime tests | **partial** (ADR 0014 D4 replacement test & `RELEASE-PLAN.md` edit pending) | `PublishTaskUseCaseTest`, `PublishBannerPresenterTest` (`476e447`/`3e795dd`); `PublishViewModelTest` (CI unverified) | no (`PublishConfirmScreenTest`, `PublishProgressScreenTest`, `PublishFlowTest` never run in CI) | no | no |
| `T3-401` / 3.4 (`PR3-024`) | `HaltUseCase`, `ResumeUseCase` + recorded cause | **yes** | `HaltAndResumeUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified — `79bde91` failed; post-sync unverified) | no | no (`PR3-042`) | no |
| `T3-402` / 3.4 (`PR3-023`) | `ReconcileUseCase` (`MERGED:`/`CLOSED:`) | **yes** | `ReconcileUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified) | no | no (`PR3-042`) | no |
| `T3-403` / 3.4 (`PR3-022`) | `UploadSubmissionUseCase` + SAF picker + `cbridge` checks | **yes** | `UploadSubmissionUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified) | no (SAF `OpenDocument` runtime unverified) | no (`PR3-042`) | no |
| `T3-404` / 3.4 (`PR3-025`, `PR3-026`, `PR3-027`) | `Decide`, `SetTaskStatus`, `CreateWorkOrder` deferral | **partial** (`DecideFrameworkUseCase` still defaults pre-D2 `ContractBlocked`; `SetTaskStatus` lacks gateway `POST` route per D3; `CreateWorkOrderUseCase` deferred per D5 — out of the Hilt graph, documented alternative shown, PR #73) | `DecideFrameworkUseCaseAndViewModelTest`, `SetTaskStatusUseCaseTest`, `CreateWorkOrderUseCaseTest`, `Stage34OperationRepositoryTest` (CI unverified — `79bde91` failed missing `planDecide`; post-sync unverified); `WorkOrderDeferralTest` (PR #73, CI green on `cceb72f`) | no (`DecisionScreenTest` never run in CI) | no (D3 route missing + `PR3-042`) | no (policy decisions D2/D3/D5 accepted; code/tests unaccepted) |
| `T3-405` / 3.4 (`—`) | Dashboard `sync` / `doctor` | **yes** | `DashboardViewModelTest` (CI unverified) | no | no | no |
| `T3-501` / 3.5 (`PR3-030`) | Per-op min auth tier & summary reason | **yes** | `ConfirmationTierPolicyTest`, `ConfirmAndMintTierEnforcementTest` (CI unverified — `75bb8e9` failed, `0ec3923`–`1651db9` cancelled) | no | no | no (BD-3.1(a) D5 confirmed; code/tests unaccepted) |
| `T3-502` / 3.5 (`PR3-031`) | No-biometric typed prefix path, no silent downgrade | **yes** | `ConfirmationTierPolicyTest`, `ConfirmAndMintTierEnforcementTest`, `PublishViewModelTest` (CI unverified) | no (device/emulator `PR3-031` record absent) | no | no |
| `T3-503` / 3.5 (`PR3-032`) | Key invalidation, `NeedsReenrollment`, gateway revoke | **partial** (`NeedsReenrollment` UI deferred to Stage 4.4) | `RevokeDeviceKeyUseCaseTest`, `DeviceEnrollmentStoreTest`, `ConfirmAndMintCapabilityUseCaseTest` (CI unverified — `75bb8e9` failed missing `FakeGatewayApi.revokeKey`; post-sync unverified) | no (crypto `androidTest` files are `SCAFFOLD PLACEHOLDER`) | no (live `/admin/revoke` unverified) | no |
| `T3-601` / 3.6 (`PR3-033`) | `ArchitectureTest` (5 invariants) | **partial (1 of 5 checks only)** | `ArchitectureTest` (`.mint(` check only green at `476e447`/`3e795dd`; 4 checks missing) | no (n/a) | no (n/a) | no |

---

## Manual Pipeline module detail

The `manualpipeline` feature module (`app/src/main/kotlin/com/inscopelabs/buildengine/manualpipeline/`, 198 files) landed on `main` in `c63adbf` **outside the staged release plan** — it has no stage row above, and it arrived with **13 Kotlin compile errors**, so `:app:compileDebugKotlin` failed and `android-ci` has been red on `main` since that commit (`43931514001` → failure). The module was also **unregistered**: no ADR, no register item, and no tests.

PR #75 (`feat/manual-pipeline-poc`) repairs the compile errors, adds the module's first test suite (**121 JVM tests**), and fixes a layering violation those tests exposed. CI is green on `44b5022` (`43934686186`: `:app:testDebugUnitTest` **678/678, 0 failed, 0 ignored**; `:app:assembleDebug` succeeded). **Merged into `main` as `5c3a0e2`** (PR #75, merged by the operator) — **not operator-accepted**.

| Area | Implemented in code | JVM-tested (test class + CI SHA) | Android-runtime-tested | Integration-tested | Operator-accepted |
|---|---|---|---|---|---|
| Task discovery, work-order interpretation, advisory eligibility (MP-001, MP-002, MP-004) | **yes** (`c63adbf`; `InterpretWorkOrderUseCase` never invents an `orderId`) | `InterpretWorkOrderAndEligibilityTest` (12), `ManualPipelineValidatorsTest` (17) (`44b5022`) | no | no | no |
| Artifact integrity and immutable upload (MP-005, MP-006, MP-008) | **yes** (`zipSha256` computed from the exact uploaded bytes; one archive per submission) | `ArtifactIntegrityAndHandoffTest` (21), `SubmitUploadUseCaseTest` (9) (`44b5022`) | no (SAF picker runtime unverified) | no (`PR3-042`) | no |
| Lifecycle transitions (MP-003) | **partial** — client-side `POST /plans/{id}/status` exists (`GatewayApi.transitionPlanStatus`), but Gateway A registers `GET /plans/:id/status` only (**PR3-026 / ADR 0014 D3**), so transitions cannot succeed against the deployed gateway. `SET_DONE` is additionally held (`SET_DONE_AWAITS_SEAL_ROUTE`) because a console upload produces no Gateway C seal and no route defines one (**BE-MP-12**) | `RequestTransitionUseCaseTest` (16), `TransitionPreconditionValidatorTest` (12) (`44b5022`) | no | no (route missing + `PR3-042`) | no |
| Recovery and handoff (MP-010, MP-014) | **yes** (lookup-only; `PRE_TRANSFER_HANDOFF_READY` requires the status read back as `completed`) | `ReconcileOperationUseCaseTest` (9), `ArtifactIntegrityAndHandoffTest` (21) (`44b5022`) | no (`PR3-041` WorkManager) | no (`PR3-042`) | no |
| Failure vocabulary and refusal classification (MP-012) | **yes** (`fromGatewayCode` / `fromThrowable`; the contract's `SEAL_NOT_ATTESTED` and `SEAL_FACT_UNAVAILABLE` reach the seal-refusal surface) | `FailureCategoryMapperTest` (16) (`44b5022`) | no | no | no |
| Module architecture rules (module-scoped extension of PR3-033) | **yes** (`domain`/`ui` independent of the host app; `ui` never reaches `data`; no HTTP, shell, Git, or GitHub client; no capability persisted) | `ManualPipelineArchitectureTest` (9) (`44b5022`) | no (n/a) | no (n/a) | no |

**OPEN (1):** `POST /plans/{id}/status` is not implemented in Gateway A. The module is client-complete against contract rev 1.2 but cannot advance a task status until the route exists (I-10 — a missing contract route is a release blocker, not permission to invent an endpoint).

**OPEN (2):** no seal route exists for a console upload, so `SET_DONE` is held locally and can never be dispatched. Resolution is `BE-MP-12` (or an operator-approved operator-executor exception to the seal rule), and it needs an ADR.

**OPEN (3):** the module has **no ADR and no register item**. The manual pipeline's approval, the `SET_DONE` question, and the archive-building question (build the archive from operator-selected files, or accept a prepared ZIP) are operator decisions that have not been recorded.

**OPEN (4):** Android-runtime and device evidence are absent (instrumented CI `PR3-043`); all evidence above is JVM-level with fakes.

## Plan Management Authoring (PMA) detail

PMA is the operator-facing **plan authoring** interface for Build Engine plans, added in PR #76 (`feat/pma-authoring`). It fills a real gap: the console could read plans (`PlanListScreen`, `PlanDetailScreen`) but had no interface anywhere between "the list of plans the gateway knows about" and "a plan somebody wrote" — `GatewayApi` exposed no plan write at all, and the gateway's plan surface is a simplified catalogue record (`PlanDO`) that cannot hold a plan document.

CI is green on `ea3a5fb` (run `43956589443`: `:app:testDebugUnitTest` **747/747, 0 failed, 0 ignored** — 678 before PMA plus 69 new; `:app:assembleDebug` succeeded). **Not merged, not operator-accepted.**

A **draft** is a plan document the operator is writing on the device. It mirrors the canonical Build Engine plan directory field for field — `plan.json`, `stages.json`, `tasks/*.json`, `coverage_exempt.json` — and the identifiers are derived the way the canonical tooling derives them: a task id is `NNNNN-PHASE-TTT-SS`, the stage is the hundreds digit of the task number, and the task number is stable, so reordering the task list changes no identifier and breaks no dependency.

| Area | Implemented in code | JVM-tested (test class + CI SHA) | Android-runtime-tested | Integration-tested | Operator-accepted |
|---|---|---|---|---|---|
| Draft authoring (create, edit, save, reopen, discard) — MP Stage 1 | **yes** (drafts are local; the plan list shows them beside the gateway's plans) | `PlanAuthoringViewModelTest` (17) (`ea3a5fb`) | no | no | no |
| Canonical validation and preview — MP Stage 2 (local half) | **yes** (the canonical rule set: identifier shapes, ascending stage order, declared phases, safe relative paths, deliverable roots and patterns, dependency resolution and cycles; the preview materialises the canonical documents) | `PlanDraftValidatorTest` (24), `PlanDraftAllocatorTest` (13), `PlanDraftUseCasesTest` (15) (`ea3a5fb`) | no | no | no |
| Identifier allocation against authoritative repository state | **no** — the plan number belongs to the Build Engine's plan tooling (`plans/counter.json`), so PMA does not mint one; identifiers carry a visible `<NNNNN>` placeholder and stay provisional | — | no | no | no |
| Gateway draft and submission APIs — MP Stage 3 | **no** — Gateway A has no plan-authoring route (no draft store, no revision CAS, no submission status lookup). The plan routes it does have (`PUT /plans/{id}`, `POST /plans/{id}/validate`) operate on the simplified catalogue record, which is not the canonical plan store | — | no | no | no |
| Build Engine workflow integration — MP Stage 4 | **no** — no workflow materialises a submitted plan into `plans/<plan_id>/` and opens the reviewable pull request | — | no | no | no |
| Submission | **blocked and reported** — the Submit action states exactly which pieces are missing rather than offering a button that does nothing; the canonical server-side validator is reported as unavailable, so a local pass is never presented as the canonical verdict | `PlanDraftUseCasesTest` (15) (`ea3a5fb`) | no | no | no |

**OPEN (1):** PMA has **no ADR and no register item**. The doc's Stage 0 — the draft schema, the API contract, the error model, the size limits, the authorisation model, the authoritative identifier reservation, the draft retention policy, and the documented distinction between a draft record, a repository plan and the PlanDO read projection — is an operator decision that has not been recorded.

**OPEN (2):** the plan number and task identifiers are not allocated against repository state. The canonical allocator is the Build Engine's plan tooling, which refuses a number another plan already holds; PMA cannot check repository uniqueness from the device.

**OPEN (3):** the repository write path does not exist on either side of the boundary — no gateway plan-authoring route, no Build Engine plan workflow. Until both exist, a plan cannot be submitted, so no acceptance criterion of §9 that depends on a pull request can be met.

**OPEN (4):** Android-runtime and device evidence are absent; all evidence above is JVM-level. The tests drive an in-memory draft repository, so the device store (`PlanDraftStore`, DataStore) is not exercised off-device either.

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
