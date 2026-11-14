# Stage 3.4 Report — Remaining Operations (`T3-401` to `T3-405`; `PR3-022` to `PR3-027`)

- **Timestamp (UTC):** `2026-11-14T13:41:23Z`
- **Plan Reference:** `RELEASE-PLAN.md` Stage 3.4 (`T3-401`, `T3-402`, `T3-403`, `T3-404`, `T3-405`)
- **Remediation Register Reference:** `docs/release/PHASE-3-REMEDIATION-REGISTER.md` (`PR3-003`, `PR3-022`, `PR3-023`, `PR3-024`, `PR3-025`, `PR3-026`, `PR3-027`)
- **Recorded Stage 3.4 Status:** `implemented in code; not accepted`

---

## 1. Scope & Architecture Verification

1. **Single Mint Path (`ArchitectureTest` + `grep`):** `.mint(` in `app/src/main/kotlin/` appears exclusively inside `ConfirmAndMintCapabilityUseCase.kt` (invoking `CapabilityMinter.mint`). `ArchitectureTest` passes (`1 passed, 0 failed`).
2. **Shared Journal Executor:** All Stage 3.4 write operations (`upload`, `reconcile`, `halt`, `resume`, `setTaskStatus`, `decide`) route through `OperationRepositoryImpl` (`executeWithJournal` / `executeDecide`), preserving intent-before-send, single-use 32-hex `idempotencyKey`, single-send/lookup recovery, and live-head confirmation before reporting `OperationOutcome.Succeeded`.
3. **Scaffold Cleanup (`G-09`):** Removed Stage 3.4 `SCAFFOLD` / `TODO` markers from `OperationRepository.kt`, `OperationRepositoryImpl.kt`, `WorkOrder.kt`, `CreateWorkOrderUseCase.kt`, and all Stage 3.4 domain/data/UI files. Remaining markers in `app/src/main/kotlin/` are strictly Stage 4.3 (`SubmissionReviewScreen.kt`), Stage 4.4 (`DecisionHistoryScreen.kt`, `AuditInspectionScreen.kt`, `DeviceManagementScreen.kt`, `HaltResumeScreen.kt`), Stage 4.5 (`ui/theme/{Color,Theme,Type}.kt`), and Stage 5.3 (`LabScreen.kt`, `LabViewModel.kt`).
4. **Permissions:** Zero storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_*`) added; `SubmissionDocumentPicker` uses SAF `ActivityResultContracts.OpenDocument`.

---

## 2. Commands Run and Results

| Command | Result | Exact Output / Counts |
|---|---|---|
| `gradle --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug` | **PASSED** (`BUILD SUCCESSFUL`) | **65 suites, 444 tests, 0 failures, 0 errors, 0 skipped**; `androidTest` Kotlin compiled cleanly; debug APK assembled (`BUILD SUCCESSFUL in 43s`). |
| `grep -rn "\.mint(" app/src/main/kotlin/` | **PASSED** | Matches appear only in `ConfirmAndMintCapabilityUseCase.kt` (lines 162, 184, 201). |
| `grep -rnE "SCAFFOLD\|TODO" app/src/main/kotlin/` | **PASSED** | Zero Stage 3.4 markers remaining; only Phase 4 / Phase 5 markers remain. |
| `grep -rnE "plans/.*/(status\|decide)\|/status\|/decide" gateway/src docs/schemas/ docs/gateway-contract.md` | **OBSERVED** | `gateway/src/routes/decisions.ts` registers `POST /plans/:id/decide`; `gateway/src/routes/plans.ts` registers only `GET /plans/:id/status` (no `POST /plans/:id/status` Worker handler yet, vs contract §3.17). |

---

## 3. Stage 3.4 Features Implemented

- **T3-401 / PR3-024 (`HaltUseCase`, `ResumeUseCase`, `OperationRepositoryImpl.halt/resume/recordedHaltCause`):**
  - Scopes capabilities strictly to `"engine"`, trims and validates non-blank secret-free halt reasons.
  - `OperationRepositoryImpl.halt` persists encrypted `cause` alongside intent; `recordedHaltCause()` decrypts the latest halt entry (failing closed to `null` if decrypt throws); `ResumeUseCase` rejects with `MissingRecordedCause` if no recorded cause exists and queries `PlanRepository.getStatus()` after execution to expose authoritative `halted` state.
- **T3-402 / PR3-023 (`ReconcileUseCase`, `OperationRepositoryImpl.reconcile`):**
  - Scopes `OperationType.RECONCILE` to task branch/head, preserves gateway `MERGED:` / `CLOSED:` strings via `OperationOutcome.Succeeded.outcome` -> `ReconcileDisposition.Merged` / `Closed`, and performs zero local git operations.
- **T3-403 / PR3-022 (`SubmissionArchivePreparer`, `UploadSubmissionUseCase`, `SubmissionDocumentPicker`, `OperationRepositoryImpl.upload`):**
  - Reads through SAF `OpenDocument`, rejects empty files, >25 MiB payloads (`MAX_UPLOAD_BYTES = 25 * 1024 * 1024` matching `gateway/src/config/constants.ts`), non-ZIP/corrupt archives, path traversal entries, and denied `engine/`, `plans/`, `.github/` paths.
  - Computes SHA-256 and Base64 from one immutable `ByteArray` in memory, does not mutate the original URI, and omits `zipBase64` from Room journal entries and `Logger`.
- **T3-404 / PR3-025 (`DecideFrameworkUseCase`, `DecisionViewModel`, `DecisionScreen`, `DecideDto`, `GatewayEndpoints.planDecide`):**
  - Captures required `attribution`, `rationale`, `scope`, `evidence`, `decision`, and optional `notes`.
  - Fails closed by default (`contractSchemaSettled = false`) with `DecideResult.ContractBlocked` citing `PR3-003` / `PR3-025` so unapproved wire fields are never invented; provides `contractSchemaSettled = true` seam for unit verification of single-mint + journal execution + DTO schema keys.
- **T3-404 / PR3-026 (`SetTaskStatusUseCase`, `PlanStatusTransitionDto`, `OperationRepositoryImpl.setTaskStatus`):**
  - Enforces allowed transitions (`PENDING -> IN_PROGRESS|DONE`, `IN_PROGRESS -> DONE`), requires completion evidence when transitioning to `DONE`, mints `SET_IN_PROGRESS` or `SET_DONE`, serializes strictly to `plan-status-transition.schema.json` keys, and refreshes authoritative `Task` and `PlanStatus` on `Succeeded`.
- **T3-404 / PR3-027 (`CreateWorkOrderUseCase`, `WorkOrder`):**
  - Scaffold markers removed under BD-1.3 Option B; invokes `CreateWorkOrderResult.UnsupportedByContract` without minting or calling the network.
- **T3-405 (`SyncBranchUseCase`, `GetDoctorReportUseCase`, `DashboardViewModel`, `DashboardScreen`):**
  - Adds `SyncStatus`, `DoctorReport`, and `DoctorCheck` read models; wires explicit `syncBranch()` and `runDoctor()` triggers on Dashboard with idle init (zero network calls on `init`).

---

## 4. Tests Added / Updated & What Each Proves

### A. JVM Unit Tests (`app/src/test/kotlin/com/inscopelabs/buildengine/` — 27 new Stage 3.4 tests + 7 DashboardViewModel tests, all passing)

1. **`HaltAndResumeUseCaseTest.kt` (5 passed):**
   - `halt mints engine-scoped capability, trims reason, and queries authoritative halted status` — proves `"engine"` scope, trimmed reason, single biometric prompt, and authoritative `PlanStatus.halted` readback.
   - `halt rejects task-scoped branch, blank reason, and forbidden secret text before prompt or send` — proves pre-mint rejection and 0 prompts/sends.
   - `resume refuses before prompt or send when no recorded halt cause exists in journal` — proves `ResumeResult.MissingRecordedCause` fail-closed gate.
   - `resume uses recorded halt cause, rejects task branch, and queries authoritative state` — proves task-branch rejection, journal cause reuse, and authoritative post-resume state readback.
   - `declined confirmation for halt or resume sends nothing` — proves declined prompt aborts without sends.
2. **`ReconcileUseCaseTest.kt` (4 passed):**
   - `confirmed reconcile parses MERGED and CLOSED outcomes from gateway` — proves task scope minting and `MERGED:` / `CLOSED:` `ReconcileDisposition` mapping.
   - `non-success reconcile outcomes propagate without disposition or success upgrade` — proves refusals/conflicts/timeouts/uncertain/recovery outcomes are never upgraded to success or disposition.
   - `invalid submissionId or non-task branch fails before prompt or send` — proves pre-mint input validation.
   - `head drift refuses reconcile before prompt or send` — proves `MintResult.HeadDrifted` abort before prompt/send.
3. **`UploadSubmissionUseCaseTest.kt` (3 passed):**
   - `empty, oversized, corrupt, path-traversal, and denied-path archives fail before prompt or send` — proves `cbridge`/gateway pre-checks (`0` bytes, `>25 MiB`, invalid ZIP, `../`, `/abs`, `engine/`, `plans/`, `.github/`) fail prior to confirmation/send.
   - `valid archive computes SHA-256 over immutable bytes matching base64 decode and keeps original unchanged` — proves SHA-256 digest matches decoded `zipBase64` and original `ByteArray` is untouched.
   - `journal persistence and logs never contain zipBase64, capability signature, or nonce` — proves Room journal fields, cipher plaintexts, and captured logs contain zero `zipBase64`, secrets, or nonces (`toString()` redacted).
4. **`SetTaskStatusUseCaseTest.kt` (5 passed):**
   - `invalid transitions are rejected before prompt or send` — proves full negative transition matrix fails closed prior to biometric prompt/send.
   - `transitioning to DONE requires non-blank completion evidence before prompt or send` — proves `MissingEvidence` enforcement.
   - `valid PENDING to IN_PROGRESS and IN_PROGRESS to DONE mint scoped capability and refresh authoritative state` — proves `SET_IN_PROGRESS` (`in_progress`) and `SET_DONE` (`completed`) minting plus authoritative task & plan refresh.
   - `refused transition skips authoritative refresh` — proves non-success skips refresh calls.
   - `PlanStatusTransitionRequestDto serializes strictly to plan-status-transition schema keys` — proves exact JSON key parity.
5. **`DecideFrameworkUseCaseAndViewModelTest.kt` (5 passed):**
   - `missing required attribution, rationale, scope, or evidence returns InvalidTarget before prompt or send` — proves local T3-404 field validation.
   - `default configuration fails closed with ContractBlocked under PR3-003 without minting or sending` — proves fail-closed default guard.
   - `DecisionViewModel exposes PR3-003 blocker, disables submission, and never sends when blocked` — proves ViewModel readiness/disable guard.
   - `when schema is settled, decide mints single DECIDE capability and executor keeps missing headSha as UncertainCompletion` — proves T3-302 invariant when `DecideAckDto` omits `headSha`.
   - `DecideRequestDto serializes strictly to decide schema properties` — proves exact JSON key parity.
6. **`CreateWorkOrderUseCaseTest.kt` (1 passed):**
   - `invoke returns UnsupportedByContract citing BD-1-3 and PR3-027` — proves BD-1.3 Option B deferral.
7. **`Stage34OperationRepositoryTest.kt` (4 passed):**
   - `halt records cause in encrypted journal, resume clears it, and decrypt failure fails closed` — proves encrypted cause persistence, clearance on resume, and fail-closed decrypt error handling.
   - `reconcile propagates gateway MERGED outcome when accepted and live head matches` — proves `OperationOutcome.Succeeded.outcome` forwarding.
   - `setTaskStatus sends transition DTO, verifies live head, and propagates newStatus` — proves wire submission + live-head check + status propagation.
   - `upload forwards sha256 and base64 and verifies live head` — proves upload DTO fields and live-head check.
8. **`DashboardViewModelTest.kt` (7 passed — 5 Stage 4.1 + 2 T3-405 tests):**
   - Added `syncBranchCallCount`/`doctorCallCount` zero-call assertions to `init` test, plus `syncBranch updates diagnosticsState on success and failure` and `runDoctor updates diagnosticsState on success and failure`.

### B. Android Instrumentation Tests (`app/src/androidTest/` — compiled cleanly; runtime unverified in-agent)

- **`DecisionScreenTest.kt` (`decisionScreenShowsBlockerAndKeepsSubmitDisabledWhenFieldsPopulated`):** Verifies `DecisionScreen` displays `decision_contract_blocker` and keeps `decision_confirm_button` disabled even when all required fields are populated.

---

## 5. Open Blockers & Deferrals

1. **OPEN (`PR3-003` / `PR3-025` — `decide` contract reconciliation):** `docs/schemas/decide.schema.json` enforces `additionalProperties: false` without `attribution`/`rationale`/`scope`/`evidence` and omits `headSha`/`branch` in `DecideAck`. `DecideFrameworkUseCase` & `DecisionScreen` fail closed (`ContractBlocked`) until reconciled.
2. **OPEN (`PR3-026` — Worker `POST /plans/:id/status` handler missing in `gateway/`):** Client matches `docs/gateway-contract.md` §3.17 and `plan-status-transition.schema.json`; `gateway/src/routes/plans.ts` currently registers only `GET /plans/:id/status`.
3. **OPEN (`order` capability identifier source — PR3-003 / BD-1.3):** Unchanged from Stage 3.3; capability minting still requires a contract-defined `order` source.
4. **DEFERRED (`PR3-027` / BD-1.3 Option B):** `CreateWorkOrderUseCase` returns `UnsupportedByContract` and mints/sends nothing.

---

## 6. Unverified In-Agent

- **Compose UI & Android Instrumentation Runtime (`connectedDebugAndroidTest`):** `DecisionScreenTest` (and Stage 3.3 Compose tests), real Android Keystore `AndroidKeystoreJournalCipher`, biometric/device-credential prompts, SAF `OpenDocument` file picker, Room SQLite runtime, and WorkManager execution cannot run without an emulator or physical device.
- **Controlled / Live Gateway A Integration:** Live HTTP roundtrips for `upload`, `reconcile`, `halt`, `resume`, `decide`, `transitionPlanStatus`, `sync`, and `doctor` against Gateway A.
- **Git / GitHub Actions CI / Vector Script:** Commit/push and `generate_vectors.py` (`cryptography` module absent) are outside this sandbox.

---

## 7. Protected Paths & Scope Confirmation

Confirmed that **zero** files were modified or created under any protected path or `gateway/**`:
- Untouched: `gateway/**`, `.github/workflows/*.yml`, `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `version.properties`, `build-logs/**`, `network_security_config.xml`, `docs/gateway-contract.md`, `docs/schemas/**`.
