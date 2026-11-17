# Manual Pipeline (`manualpipeline`) — Production Readiness Register

**Document ID:** `OC-MP-PRR-001`
**Repository:** `inscope-labs/operator-console`
**Revision:** `1.2` — re-verified against the repository at the verification head below after the contract moved to revision 1.3 (ADR 0016 D-1); every change from revision 1.1 is listed in Appendix A
**Install path:** `docs/release/MANUAL-PIPELINE-READINESS-REGISTER.md`, via a branch and pull request that the operator merges
**Verification head:** `1f949ef` (`main` on 2026-11-17, the merge of PR #82). Observations below were made at this commit
**Design basis:** Build Engine — Manual Pipeline Module Assessment (Revision 2), committed beside this register as `docs/release/MANUAL-PIPELINE-ASSESSMENT-V2.md`; `§`, `Q`, `I-nn` and “test n” references below point into it. The assessment was verified against `operator-console` `560882c` and `build-engine` `9309a91`
**Controlling contract:** `docs/gateway-contract.md` **revision 1.3** (ADR 0016) for what the console may call — `SET_IN_PROGRESS` only; `SET`, `SET_DONE` and `completed` are reserved and refused with `400 BAD_ENCODING`. Where it differs from the assessment or from the build-engine `TASK-STATE-TRANSITION-CONTRACT.md`, the difference is a reconciliation item (MP-0), not something this register settles
**Release gate status:** **PENDING VERIFICATION.** The feature is gated by the blockers in section 2. No row is `PASS`

---

## 1. Scope and how to read this register

**Scope.** The `manualpipeline` feature: the package `com.inscopelabs.buildengine.manualpipeline` inside the single `:app` Gradle module (198 files: `domain`, `data`, `ui`, `di`, `recovery`, `public`, `util`). It is **not** a separate Gradle module. `ManualPipelineModule.kt` is an empty Hilt marker (`@Module @InstallIn(SingletonComponent::class) object`); the real bindings live in six sibling modules under `manualpipeline/di/` (`Bindings`, `Executor`, `Gateway`, `Journal`, `Provides`, `Recovery`), all installed in `SingletonComponent`.

**Status vocabulary.** `UNVERIFIED` (no closure evidence), `BLOCKED` (cannot be exercised until a named blocker clears), `FINDING` (a defect or gap was observed at the verification head and must be fixed before the row can pass), `PASS` (acceptance evidence attached and independently reviewable). “Code exists”, “agent report says complete” and “JVM tests pass” are not closure evidence on their own.

**Dependency order.** `BLD → DI → API → KOT → SEC → LIF → OBS → TST`. Build, DI and API form the structural base; Kotlin/concurrency gives runtime correctness; SEC settles the credential and scope model before any transition code is judged; LIF is the feature itself; OBS records lifecycle outcomes; TST verifies all of it. The `Depends on` column carries cross-row dependencies. DI-01 and LIF-14 are mutually referencing; they are closed together.

**Reading rule.** A module can pass BLD, DI, API and SEC and still be NOT READY if any LIF row is unverified, blocked or a finding. The acceptance criterion is the lifecycle, not the marker module.

## 2. Blockers that gate the LIF category

| Blocker | Verified state at `1f949ef` | Effect |
|---|---|---|
| Issue 1 — no documented route from a Gateway A upload to a Gateway C seal (assessment §5) | Open per the assessment: without a route, `SET_DONE` would be refused with `SEAL_NOT_ATTESTED`. Three resolution options are listed there | LIF-07 cannot pass; the module must not call `SET_DONE` and never records `done` locally until Decision 1 lands |
| PR3-026 / ADR 0014 D3 — `POST /plans/{id}/status` | **Still absent on the gateway.** `gateway/src/routes/plans.ts` registers `GET /plans/:id/status` only; no `POST` route exists. The **client** side is now closed for `SET_IN_PROGRESS` (`4ba2c90`: `SetTaskStatusUseCase` accepts only `SET_IN_PROGRESS` and rejects `DONE` with `UnsupportedByContract` before any prompt or gateway call) and contract rev 1.3 settles what the gateway must accept (`SET_IN_PROGRESS` only; `SET`, `SET_DONE` and `completed` reserved, refused with `400 BAD_ENCODING`) | LIF-02 to LIF-11 cannot be exercised end to end |
| PR3-021 — Android task model lacks the work-order `order` | **Changed on both sides.** The contract gap is closed: contract rev 1.2 §3.6 makes `order` a required field of the `GET /tasks/next` item, and the Android client now carries it — `TaskSummaryDto.order` (PR #74) and `PublishOrderResolver`, which returns `task.order.takeIf { it.isNotBlank() }` and fails closed rather than minting an empty order. **Still open:** the gateway's `gateway/src/routes/tasks.ts` does not emit the field at all, so the identifier has no source end to end. The client field still defaults to `""` and is not enforced non-blank (PoC tasks 8 and 9) | LIF-01 task-identification path incomplete |
| PR3-020 — shared journal-backed executor | `SharedExecutorAdapter` binds `ExecutorPort` to the host `OperationRepository`; closure of PR3-020 itself is not recorded | LIF-04 and KOT-04 stay unverified until PR3-020 closes |
| Decision 5 — Gateway A transition table | **Partly settled 2026-11-17.** ADR 0016 D-1 fixed the reserved set: contract rev 1.3 supports `SET_IN_PROGRESS` only, and `SET`, `SET_DONE` and `completed` are reserved and refused with `400 BAD_ENCODING` — the assessment (§1.2), which treats `SET` as legal to any non-terminal status, is no longer compatible with the console's contract. What Decision 5 still has to settle is whether and when the reserved values return (seal route and completion-evidence rule) and whether the gateway should ever answer a reserved transition with `TRANSITION_NOT_ALLOWED` instead of `400 BAD_ENCODING` | LIF-02 and LIF-08 provisional until Decision 5 is closed against one table |
| Decision 3 / Q14 — server-side task lease | Undecided; no lease exists in the published contract | LIF-22: until decided, the optimistic guards are the only protection and the UI must say so |
| No ADR and no register item for the module | **Partly closed.** This register is now the register item for the module (installed as `OC-MP-PRR-001`, revision 1.2). An ADR recording the operator's decisions — approval of the manual pipeline, the `SET_DONE` question, archive building from selected files versus a prepared ZIP, and the transition table (assessment Decision 7) — is still required before release; ADR 0016 settled only the `SET_DONE` deferral, not the module approval. `docs/STATUS.md` records the missing ADR and the three undecided operator decisions as open | This register is the register item; an ADR recording those decisions is still required before release (assessment Decision 7). The seven assessment decisions are: 1 seal route, 2 archive building, 3 lease, 4 read-only credential for Gateway A to read the seal fact, 5 transition table, 6 handoff evidence, 7 module ADR |

## 3. Register

Columns: ID, check, acceptance evidence, status, owner, depends on. “Observed” notes are reviewer observations at the verification head above, not closure evidence.

### BLD — Build and configuration

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| BLD-01 | Release variant with R8/ProGuard builds with the feature. | CI runs `./gradlew :app:assembleRelease` with `isMinifyEnabled = true`; mapping file archived. Observed: release minify is on; **no CI job builds release** (`android-ci.yml` runs only `:app:testDebugUnitTest` and `:app:assembleDebug`). | FINDING | Android Lead | — |
| BLD-02 | The feature's cross-module surface is limited to `manualpipeline.public`. | Enforced by test, not by `explicitApi()`. Observed: `explicitApi` is not enabled and does not apply to an application module; the boundary is a package convention. | FINDING | Module Owner | — |
| BLD-03 | Hilt, Kotlin, KSP and AGP versions pinned; dependency scan clean of critical CVEs. | Version catalog pins (observed: AGP 8.13.2, Kotlin 2.3.20, KSP 2.3.12, Hilt 2.58); scan report attached. | UNVERIFIED | Build Engineer | — |
| BLD-04 | Merged manifest requests exactly `INTERNET` and `USE_BIOMETRIC`; no storage permission (ADR 0002). | Manifest merge report plus an assertion test. Observed: `app/src/main/AndroidManifest.xml` declares exactly those two. | UNVERIFIED | Security Champion | — |

### DI — Hilt

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| DI-01 | Every `@Singleton` binding (assessment Q12 resume semantics, §1A.3) across all six `manualpipeline/di` modules is stateless or has an explicit process-death strategy. | Reviewer sign-off per binding. Observed: `SavedStateHandle` is not used anywhere in the module; recovery relies on the Room journal and `ManualPipelineRecoveryWorker`. | UNVERIFIED | DI Reviewer | BLD-03, LIF-14 |
| DI-02 | Provided and bound dependencies are thread-safe or guarded. | Static analysis or concurrency test; no unguarded shared mutable state. | UNVERIFIED | Feature Owner | DI-01 |
| DI-03 | Hilt generation succeeds for the release variant. | `./gradlew :app:kspReleaseKotlin` clean. KSP only; the project does not use KAPT. | UNVERIFIED | Android Lead | BLD-01 |
| DI-04 | Capability minting is reachable only through `ConfirmAndMintCapabilityUseCase`, never from a worker or non-confirm path (I-01, I-02). | Architecture test enumerating callers. Observed: `ExistingMintingAdapter` wraps the host use case; the existing test “never calls the capability minter directly” covers part of this. | UNVERIFIED | DI Reviewer | DI-01 |

### API — Module boundary

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| API-01 | Host code imports the feature only from `manualpipeline.public`. | Build-failing test. Observed: `ManualPipelineArchitectureTest` checks that `domain` and `ui` do not import host packages; **no test checks host-to-feature imports**. No detekt or ArchUnit is configured; the rule must be added as a source-scan test or the tools adopted. P0 if violated. | FINDING | Module Owner | BLD-02 |
| API-02 | `manualpipeline.public` is the sole entry point; no new public types leak. | API surface diff reviewed. | UNVERIFIED | Module Owner | API-01 |
| API-03 | `ui` does not import `data`; `domain` does not import `ui` or `data`. | Observed: both rules exist in `ManualPipelineArchitectureTest`; keep them required CI. | UNVERIFIED | Android Lead | — |
| API-04 | Only `core.network` speaks HTTP. | Observed: test “never speaks HTTP directly” exists and a grep finds no OkHttp, Ktor client or `HttpURLConnection` in the module. | UNVERIFIED | Android Lead | — |
| API-05 | No competing credential store, journal or repository abstraction. | Test asserting the module consumes the host credential store, Room journal and shared executor. Observed: `data/journal` and `data/integration` adapters wrap host types. | UNVERIFIED | Android Lead | — |

### KOT — Kotlin and concurrency

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| KOT-01 | No `runBlocking`, blocking I/O or heavy work on Main. | Static check in CI. Observed: no `runBlocking` in the module; no detekt configured, so a source-scan test is needed. | UNVERIFIED | Kotlin Reviewer | BLD-01 |
| KOT-02 | A failing dependency does not crash the app. | Unit test proving failure isolation. | UNVERIFIED | QA Engineer | DI-02 |
| KOT-03 | No `Runtime.exec`, `ProcessBuilder`, shell or local Git (I-09). | Observed: test “no alternative execution channel” exists; grep is clean. | UNVERIFIED | Security Champion | — |
| KOT-04 | The module uses the shared executor (PR3-020); no private dispatcher or pool. | DI graph review. Observed: `SharedExecutorAdapter` delegates to the host `OperationRepository`. | BLOCKED (PR3-020) | Android Lead | DI-01 |

### SEC — Security and authority

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| SEC-01 | No secrets or API keys in the module. | Secret scan of `manualpipeline/**` with zero findings. | UNVERIFIED | Security Champion | — |
| SEC-02 | No class, preference key or request field accepts a GitHub token (ADR 0002, I-02, I-09). | Observed: test “no direct GitHub client or credential shape” exists. P0 if violated. | UNVERIFIED | Security Champion | — |
| SEC-03 | Journal, workflow tables, audit records and logs contain no capability, signature, nonce, private key or raw credential (I-04, I-12). | Schema review of `WorkflowRecordEntity`, `OperatorActionEntity`, `HandoffEvidenceEntity`, `DeliverableManifestEntity`; test “no module record declares a capability signature nonce or credential” exists. | UNVERIFIED | Security Champion | — |
| SEC-04 | Module logging and audit route through the host redactor (I-04). | Unit test per forbidden field. Observed: `RedactorAdapter` exists; no `android.util.Log` use in the module. | UNVERIFIED | Security Champion | SEC-03 |
| SEC-05 | Per-environment keys, credentials and configuration cannot cross environments (I-11). | Namespacing test; a staging enrolment cannot mint for production. Observed: `EnvironmentIsolationValidator` exists. | UNVERIFIED | Android Lead | — |

### LIF — Lifecycle (the feature)

Anchored to contract rev 1.3 (§1.7 statuses, §3.17 transitions) and the assessment (invariants, Q1–Q16, §3.2).

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| LIF-01 | The module recognises one documented set of task statuses and a documented mapping between the two vocabularies in play: the build-engine canonical states (`planned`, `in_progress`, `pr_open`, `done`, `dropped`, `superseded`; assessment §1.2) and the console wire statuses (`draft`, `ready`, `in_progress`, `completed`, `blocked`; contract §1.7). Any unmapped value is fail-closed; terminal states are inert. | Mapping table committed and reviewed; parameterised test per status plus an unrecognised value. Observed: `TaskStatus` implements the wire set with `UNKNOWN`, and only `completed` is terminal; no mapping to the build-engine states is documented, and the assessment's terminal set (`done`, `dropped`, `superseded`) has no wire counterpart for the last two. | FINDING | Feature Lead | PR3-021, Decision 5 |
| LIF-02 | The module emits only operations the controlling table allows and holds no transition table of its own (§3.1, I-10). Under **rev 1.3** that is `SET_IN_PROGRESS` only; `SET`, `SET_DONE` and `completed` are reserved and refused with `400 BAD_ENCODING`. Under the assessment's table `SET` is also legal. | **The `SET` defect this row recorded is fixed** (`4ba2c90`, CI green run 44194377929): `TransitionKind.SET` no longer exists, the `SET` branches are gone from `ExistingMintingAdapter`, `JournalIntentBuilder`, `ActConfirmingViewModel` and `TransitionPreconditionValidator`, and the module's local `SET` rule is deleted. **New finding (ADR 0016 D-1, 2026-11-17):** `TransitionKind.SET_DONE` still exists and still maps to `OperationType.SET_DONE` in `ExistingMintingAdapter` and `JournalIntentBuilder`. The only thing holding it back is a local constant, `TransitionPreconditionValidator.SET_DONE_AWAITS_SEAL_ROUTE = true`, whose KDoc says to set it to `false` once BE-MP-12 lands. Contract rev 1.3 reserves `SET_DONE` outright and refuses it with `400 BAD_ENCODING` — before any seal question is reached — so flipping that constant would mint a capability the contract reserves. This is the same shape as the `SET` defect just closed. Decision 5 still governs the table. | FINDING | Feature Lead | PR3-026, Decision 5 |
| LIF-03 | One permitted transition per operator action; no batch, queued, pre-approved or background minting (I-01, Q11). | UI test: one transition per confirmation; no queued-mode surface. | UNVERIFIED | Feature Lead | DI-04 |
| LIF-04 | Every transition needs its own confirmation at the required tier; the dialog shows the full 40-character SHA, operation, plan, task and branch (ADR 0012). | Compose/Robolectric test per transition; screenshots. Observed: `TransitionConfirmationDialog`, `HeadShaDisplay` exist. | UNVERIFIED | QA Lead | PR3-020 |
| LIF-05 | Each minted capability is fresh, single use, bound to the exact branch and head, within TTL; `NONCE_REPLAYED` is surfaced and never retried (I-07). | Contract-vector and fault tests per operation. TTL value taken from the contract, not the assessment. | UNVERIFIED | Security Champion | DI-04 |
| LIF-06 | A halt is respected: the gateway refusal is shown with its code and never falls through to a write (I-08, Q3). | Negative test with halt active. The contract code is `HALT_ACTIVE`. | UNVERIFIED | QA Engineer | PR3-026 |
| LIF-07 | `SET_DONE` seal refusals fail closed: local state stays unchanged, no local “done” is recorded, no skip is offered. | Fault test per refusal. Contract revision 1.3 (ADR 0016 D-1) reserves `completed` and op `SET_DONE` and refuses them with `400 BAD_ENCODING`; the `SEAL_NOT_ATTESTED` (409) and `SEAL_FACT_UNAVAILABLE` (503) codes this row was written against come from the build-engine transition contract and are **not defined in console contract rev 1.3** — the seal question is no longer reached, because the operation is refused before it. The assessment's Q3 codes still have to be imported before this row can be tested as written (I-10). A refused nonce is spent: any later retry needs a fresh capability. | BLOCKED (Issue 1, Decision 1; contract rev 1.3 reserves `SET_DONE`) | Feature Lead | LIF-02 |
| LIF-08 | Illegal or reserved transitions are refused by the gateway and shown with the gateway's code and message. | Parameterised (status × operation) test. Assessment test 9 expects `TRANSITION_NOT_ALLOWED` (including `SET` to `done`). **Observed:** console rev 1.3 refuses reserved values with `400 BAD_ENCODING` and a wrong op with `UNKNOWN_OP`, and does not define `TRANSITION_NOT_ALLOWED`; reconcile under Decision 5. **Changed 2026-11-17:** contract revision 1.3 (ADR 0016 D-1) settled which values are reserved — `SET`, `SET_DONE` and `completed`, all refused with `400 BAD_ENCODING` — so the console no longer has to choose between the two tables for the reserved set. Only `TRANSITION_NOT_ALLOWED` remains unreconciled, and the client's `SET_IN_PROGRESS`-only path (PoC tasks 1 and 2, `4ba2c90`) is the implementation of the reserved rule on the app side. | BLOCKED (Decision 5; `TRANSITION_NOT_ALLOWED` not in console rev 1.3) | QA Engineer | LIF-02 |
| LIF-09 | Authoritative read-back after every transition before success is reported (I-06, Q12). | Integration test with a simulated out-of-band change. | UNVERIFIED | QA Lead | PR3-013 |
| LIF-10 | An uncertain write is resolved only by `GET /operations/{id}`; never resent; `404 OPERATION_NOT_FOUND` maps to recovery-required (I-05, ADR 0013). | Fault matrix (PR3-015) per uncertainty class; the recovery worker is lookup-only. Observed: `ReconcileOperationUseCase`, `RecoveryReconciler` exist. **Corrected 2026-11-17:** Gateway A **does** register the lookup route — `gateway/src/routes/operations.ts` registers `GET /operations/:id` — so the earlier note that “Lookup route is not implemented on Gateway A yet” was wrong. `OperationRecoveryWorker` enqueues unique work with `ExistingWorkPolicy.KEEP`, so recovery is lookup-only and never a resend. What is still missing is the controlled-gateway run (PR3-042) that would exercise the route end to end. | BLOCKED (ADR 0013 gateway task) | QA Lead | PR3-015 |
| LIF-11 | `HEAD_MOVED`, `STALE` and `IDEMPOTENCY_KEY_REUSED` fail closed; the journal is preserved; no auto-rebase or discard. | Conflict test per code. Observed: `ConflictReportView` exists. | UNVERIFIED | QA Lead | LIF-10 |
| LIF-12 | The UI renders each state once, with the next permitted action and the blocked reason plus the gateway code. | UI test per state. | UNVERIFIED | QA Lead | LIF-01 |
| LIF-13 | `PRE_TRANSFER_HANDOFF_READY` (assessment §3.2) holds only when its three conditions are met: (1) upload accepted with audit reference and archive digest recorded; (2) Gateway A reports the task terminal (`done` in assessment terms, `completed` on the wire), read back after the transition; (3) the operator confirmed handoff and the handoff references are available. It is a module-level state, not a task status, and the module performs no Supervisor-Agent transfer (Q8). | E2E test of all three conditions plus an architecture test that no transfer call exists. Depends on Decision 6. | UNVERIFIED | Feature Lead | LIF-07 |
| LIF-14 | Process death mid-transition leaves no phantom running state and no local “done” after a refusal. | Kill/restore test mid-execution against the journal and recovery worker (the module has no `SavedStateHandle`). | UNVERIFIED | QA Lead | DI-01 |
| LIF-15 | Failure vocabulary is preserved: `NOT_CONFIGURED`, `UNREACHABLE`, `TLS_PINNING`, `AUTH`, `STALE`, `POLICY_REFUSED`, `CONFLICT`; nothing is collapsed to a generic failure. | Mapping test per code. Observed: `FailureCategory` has these plus `UNKNOWN`; the original draft omitted `CONFLICT` and `UNKNOWN`. | UNVERIFIED | QA Lead | — |
| LIF-16 | Every terminal result keeps the operation ID and audit reference; attribution uses the enrolled identity, never typed text (I-12, Q16). | Audit schema review plus a test. | UNVERIFIED | Feature Lead | SEC-03 |
| LIF-17 | The advisory eligibility evaluator never grants permission; the gateway guards are authoritative (Q2). | Test forcing a mismatched advisory result. Observed: `EvaluateEligibilityUseCase`, `EligibilityAdvisoryPanel`. | UNVERIFIED | QA Lead | — |
| LIF-18 | A staging enrolment cannot mint for production (I-11). | Per-environment test. | UNVERIFIED | Security Champion | SEC-05 |
| LIF-19 | Where the environment disallows `in_app_confirm`, the module degrades to read-only. Missing biometric hardware must fall to `in_app_confirm` and never stop the app. | UI test in a profile that rejects `in_app_confirm`, and a device-without-biometrics test. Observed: `ReadOnlyBanner`, `ReadOnlyState` exist. | UNVERIFIED | QA Lead | LIF-04 |
| LIF-20 | The module keeps no authoritative copy of task status. | Schema review: workflow tables hold metadata only. | UNVERIFIED | Feature Lead | SEC-03 |
| LIF-22 | No local lock or ownership claim is presented as cross-device protection; until a server-side lease exists (Decision 3), the UI states that the optimistic guards (single-use nonce, head compare-and-swap, idempotency key) are the real protection (Q14). | UI test and copy review. *New row.* | UNVERIFIED | Feature Lead | LIF-05 |
| LIF-21 | The idempotency key is a client-generated 32-hex value, separate from the nonce, on every write (ADR 0013). | Unit test on `IdempotencyKey` and `BuildIdempotencyKeyUseCase`; journal test. *New row.* | UNVERIFIED | Feature Lead | LIF-10 |

### OBS — Observability

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| OBS-01 | A safe, identifiable log or metric is emitted on critical failure paths; no PII. | Instrumentation test; payload scanned. | UNVERIFIED | SRE / Feature Owner | LIF-15 |
| OBS-02 | All logging goes through the host redactor; nothing bypasses it. | Per-site unit test and a scan. (Merged with SEC-04 for the code path; this row covers the log sites.) | UNVERIFIED | Security Champion | SEC-04 |
| OBS-03 | Operator-action records link to the gateway audit reference; the local record is evidence, not a substitute (I-12). | Audit-manager test; schema review. | UNVERIFIED | Feature Lead | LIF-16 |

### TST — Testing

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| TST-01 | State × event matrix over the wire statuses; every illegal or reserved pair is refused. | Report covering LIF-01, LIF-03, LIF-08. | UNVERIFIED | QA Lead | LIF-01 |
| TST-02 | Unit coverage of module providers and use cases at or above the agreed threshold. | **No coverage tool (JaCoCo or Kover) is configured**, so the 80% target in the draft cannot be evidenced. Add the tool, then set the threshold. Observed: 121 module JVM tests (`docs/STATUS.md`). | FINDING | QA Lead | BLD-03 |
| TST-03 | `@HiltAndroidTest` proves the graph installs and no stateful singleton leaks between tests. | Instrumented report. Observed: `hilt-android-testing` is a dependency. | UNVERIFIED | QA Lead | DI-01 |
| TST-04 | Recovery: termination during a write, restart, lookup-only recovery, no resend. | Fault-matrix report (assessment §4.4 tests 11–13). | BLOCKED (LIF-10) | QA Lead | LIF-10 |
| TST-05 | Conflicts: `HEAD_MOVED`, `IDEMPOTENCY_KEY_REUSED` (§4.4 tests 10, 14). | Report. | UNVERIFIED | QA Lead | LIF-11 |
| TST-06 | Seal refusals leave local state unchanged (§4.4 test 4). | Report. | BLOCKED (LIF-07) | QA Lead | LIF-07 |
| TST-07 | Environment isolation (§4.4 test 20). | Report. | UNVERIFIED | QA Lead | LIF-18 |
| TST-08 | All 21 architecture tests listed in assessment Q17 run in CI. | `ManualPipelineArchitectureTest` has 9 tests. By name they correspond to Q17 items 11 (minter), 13 (no persisted capability), 14 (token shape; the manifest assertion is missing), 15 and 16 (HTTP and execution channel, partly) and 17 (`ui` not to `data`), plus two host-import rules that are not Q17 items. **No test appears to exist for items 1–10, 12, 18–21** (module scope, journal-before-dispatch, contract vectors, environment namespacing, read-back, lab-mode import). Name-level mapping only; a reviewer must confirm each. | FINDING | Android Lead | API-01 |
| TST-09 | End-to-end handoff verification with all three conditions of §3.2 (§4.4 test 18). | Report. | BLOCKED (LIF-07) | QA Lead | LIF-13 |

## 4. Automation and sign-off

- **Must fail the build:** BLD-01, BLD-02, DI-03, API-01, API-03 to API-05, KOT-01, KOT-03, SEC-01 to SEC-03, TST-08. Today none of these runs on a release build; CI covers debug unit tests and a debug assemble only.
- **Needs reviewer sign-off:** DI-01, DI-04, LIF-03, LIF-04, LIF-07, LIF-13, LIF-17.
- **Release gate:** every row `PASS` or an operator-accepted risk recorded in writing. A `FINDING` or `BLOCKED` row is never accepted by default.

## Appendix A — Corrections from the draft

| # | Draft statement | Correction |
|---|---|---|
| C-01 | Chat preamble and a trailing offer to paste code were included | Removed; this is a repository document |
| C-02 | Six canonical states `planned`, `in_progress`, `pr_open`, `done`, `dropped`, `superseded`, stated as the module's states | Those are the build-engine states (assessment §1.2). The console wire contract §1.7 and `TaskStatus` use `draft`, `ready`, `in_progress`, `completed`, `blocked` (plus fail-closed `UNKNOWN`); only `completed` is terminal. The two vocabularies are not mapped anywhere; recorded as a finding (LIF-01). An earlier revision of this register's pull request said the six states “do not exist”; that was wrong and is corrected here |
| C-03 | Module emits `SET_IN_PROGRESS`, `SET`, `SET_DONE` | The assessment allows all three; console rev 1.2 supports only the first and last and reserves `SET`. Decision 5 is open. Code defect recorded either way (LIF-02) |
| C-04 | Scope is an inert marker plus a `:manualpipeline` module | It is a package in `:app`; the marker is empty and six sibling Hilt modules carry the bindings (section 1, DI-01) |
| C-05 | `explicitApi()` confirmed in `build.gradle.kts` | Not enabled and not applicable to an app module (BLD-02) |
| C-06 | “KSP/KAPT” | KSP only (DI-03) |
| C-07 | detekt or ArchUnit enforces boundaries | Neither is configured; enforcement is JUnit source-scan tests, and the host-import rule has none (API-01, KOT-01) |
| C-08 | Release assemble runs in CI | CI builds debug only (BLD-01) |
| C-09 | Coverage at least 80% via JaCoCo | No coverage tool is configured (TST-02) |
| C-10 | 21 architecture tests wired into CI | 9 exist (TST-08) |
| C-11 | Failure vocabulary of six codes | `FailureCategory` also has `CONFLICT` and `UNKNOWN` (LIF-15) |
| C-12 | Refusal codes `TRANSITION_NOT_ALLOWED`, `SEAL_NOT_ATTESTED`, `SEAL_FACT_UNAVAILABLE`, halt code unnamed | The codes come from the build-engine transition contract and are absent from console rev 1.2, which gives `400 BAD_ENCODING`, `UNKNOWN_OP` and `HALT_ACTIVE`; import or reconcile (LIF-06 to LIF-08) |
| C-13 | Blocker PR3-026 status unstated | Verified still absent in `gateway/src/routes/plans.ts`; `GET /operations/{id}` also pending (section 2, LIF-10) |
| C-14 | References to Assessment v2 with no copy in the repo | Assessment committed as `docs/release/MANUAL-PIPELINE-ASSESSMENT-V2.md`; references verified against it. DI-01 cited Q14 for process death; the correct reference is Q12 (Q14 is the lease). The handoff state has three numbered conditions in §3.2, not four (LIF-13, TST-09) |
| C-15 | Single status “UNVERIFIED” for everything | Added `FINDING` and `BLOCKED` so observed defects are not hidden among unverified rows |
| C-16 | Rows listed in dependency order only by category | Added a per-row `Depends on` column and the DI-01 / LIF-14 pairing note |
| C-17 | No coverage of idempotency key, missing-biometric degrade or the lease question | Added LIF-21 and LIF-22; extended LIF-19 |
| C-18 | Markdown tables lost (tab-separated text, emoji headings) | Rebuilt as valid Markdown tables |

### Revision 1.2 corrections (2026-11-17)

Re-verified against the repository at `1f949ef` after the contract moved to revision 1.3 (ADR 0016 D-1). The revision 1.1 log above is left as history; these entries correct it and record what changed.

| # | Revision 1.1 statement | Correction at revision 1.2 |
|---|---|---|
| R-01 | C-03 — “console rev 1.2 supports only `SET_IN_PROGRESS` and `SET_DONE` and reserves `SET`” | Contract **rev 1.3** reserves `SET`, `SET_DONE` and `completed`, and refuses all of them with `400 BAD_ENCODING` (ADR 0016 D-1). `SET_DONE` is no longer a supported operation, so the module's completion path is a deferral, not an unimplemented feature |
| R-02 | LIF-02 — “`TransitionKind.SET` exists and maps to `OperationType.SET_IN_PROGRESS`” | **Fixed** in `4ba2c90` (CI green, run 44194377929): `TransitionKind.SET` is deleted and the `SET` branches are gone from `ExistingMintingAdapter`, `JournalIntentBuilder`, `ActConfirmingViewModel` and `TransitionPreconditionValidator`. A **new** finding replaces it: `TransitionKind.SET_DONE` still maps to `OperationType.SET_DONE` and is held back only by a local constant (`SET_DONE_AWAITS_SEAL_ROUTE`) whose stated reason (`SEAL_NOT_ATTESTED`) is not a code console rev 1.3 defines — rev 1.3 refuses the operation with `400 BAD_ENCODING` before the seal question is reached |
| R-03 | C-13 — “`GET /operations/{id}` also pending” | **Wrong.** Gateway A registers the route: `gateway/src/routes/operations.ts` registers `GET /operations/:id`. What is missing is the controlled-gateway run that would exercise it (PR3-042) |
| R-04 | C-12 — refusal codes “absent from console rev 1.2” | They are still absent from **rev 1.3**: the console contract defines `400 BAD_ENCODING`, `UNKNOWN_OP` and `HALT_ACTIVE`, and no `TRANSITION_NOT_ALLOWED`, `SEAL_NOT_ATTESTED` or `SEAL_FACT_UNAVAILABLE` (LIF-06 to LIF-08) |
| R-05 | Section 2 blockers — “PR3-021: the capability `order` has no source in the contract” | The contract now names it: rev 1.2 §3.6 makes `order` a required `GET /tasks/next` field, and the client carries it (PR #74, `TaskSummaryDto.order`, `PublishOrderResolver`). The gateway still does not emit it — `gateway/src/routes/tasks.ts` has no `order` — so the identifier has no source end to end |
| R-06 | Section 2 blockers — “PR3-026 / ADR 0014 D3: still absent” | Still absent **on the gateway** (`plans.ts` registers `GET` only). The client is now closed for `SET_IN_PROGRESS` (`4ba2c90`) and rev 1.3 settles what the gateway must accept |
| R-07 | Section 2 blockers — “No ADR and no register item for the module” | This register is now the register item. The module ADR is still missing, and ADR 0016 settled only the `SET_DONE` deferral, not the module approval (assessment Decision 7) |

## Appendix B — Not covered

This register does not audit Gateway A or C source, the host app outside `manualpipeline`, or Plan Management Authoring (PR #76). Items are re-verified against the repository head at each use; observations in this revision are dated by the verification head above.
