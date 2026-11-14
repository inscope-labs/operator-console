# Phase 3 Evidence Dossier

> **Scope notice:** This document currently records **Stage 3.3** and **Stage 3.4** evidence only. Remaining Phase 3 items (3.5, 3.6, full Gate 3 checklist, controlled-gateway integration, Android runtime proof, and overall Phase 3 acceptance) are not closed by this document. Do **not** claim Gate 3 or on-device behavior from this record.

---

## Stage 3.3 — Guarded Publish Flow (T3-301 to T3-303; PR3-020, PR3-021)

- **Status:** `implemented in code; not accepted`
- **Register Traceability:** `PR3-020` (**OPEN** — publish path implemented, remaining operations in Stage 3.4), `PR3-021` (**OPEN** — two open items below; instrumentation runtime & live gateway verification unverified in-agent)
- **Plan Traceability:** `RELEASE-PLAN.md` Stage 3.3 (`T3-301`, `T3-302`, `T3-303`)

### 1. Implementation Artifacts Verified

- `app/src/main/kotlin/com/inscopelabs/buildengine/domain/usecase/write/PublishOrderResolver.kt` — fails closed (`UnresolvedPublishOrderResolver` returns `null`) when no contract-defined `order` source exists.
- `app/src/main/kotlin/com/inscopelabs/buildengine/domain/usecase/write/PublishTaskUseCase.kt` — binds confirmation via `ConfirmAndMintCapabilityUseCase` (`.mint(` remains isolated in `ConfirmAndMintCapabilityUseCase` only), validates full 40-char SHA / `task-` branch / non-blank order, submits through shared `OperationRepository.publish`.
- `app/src/main/kotlin/com/inscopelabs/buildengine/data/repository/OperationRepositoryImpl.kt` (`publish` & `handleWriteAcknowledgement`) — persists Room journal intent before send, uses single-use idempotency key, maps responses to sealed `OperationOutcome`, and requires both gateway confirmation (`SUCCEEDED`) and a live-head check before returning `OperationOutcome.Succeeded` (otherwise `UncertainCompletion`).
- `app/src/main/kotlin/com/inscopelabs/buildengine/ui/publish/PublishViewModel.kt` — loads live task + live branch head, enforces order & tier presence and single-submission guard (`inFlight`), emits `PublishUiState` (`Idle`, `Loading`, `LoadFailed`, `Ready`, `Submitting`, `Finished`), maps outcomes through `PublishBannerPresenter`.
- `app/src/main/kotlin/com/inscopelabs/buildengine/ui/publish/PublishBanner.kt` — maps every refusal, conflict, timeout, uncertain, recovery, and pre-send authorization failure kind to a distinct `PublishBannerKind`, marking `isSuccess = true` only for `PublishBannerKind.SUCCEEDED`.
- `app/src/main/kotlin/com/inscopelabs/buildengine/ui/publish/PublishConfirmScreen.kt` — displays operation, task/plan, order (`Unavailable` fallback), branch, full 40-character head SHA, environment, tier badge, TTL, and guard summary; disables confirm when order is unresolved or tier is unknown; requires typed 7-character head prefix for `IN_APP_CONFIRM`.
- `app/src/main/kotlin/com/inscopelabs/buildengine/ui/publish/PublishProgressScreen.kt` & `app/src/main/kotlin/com/inscopelabs/buildengine/ui/components/OperationResultBanner.kt` — displays progress and terminal result banner; displays `auditRef` when present; styles only verified `isSuccess == true` as `primaryContainer` (otherwise `errorContainer`).

### 2. Scaffold Marker Status (G-09)

- Stage 3.3 main sources (`PublishTaskUseCase.kt`, `PublishOrderResolver.kt`, `OperationRepositoryImpl.publish`, `PublishViewModel.kt`, `PublishBanner.kt`, `PublishConfirmScreen.kt`, `PublishProgressScreen.kt`, `OperationResultBanner.kt`): **zero** `SCAFFOLD PLACEHOLDER` or `TODO` markers.
- `app/src/androidTest/kotlin/com/inscopelabs/buildengine/ui/publish/PublishFlowTest.kt`: `SCAFFOLD PLACEHOLDER` marker removed after replacing the placeholder smoke test with end-to-end Compose flow tests.
- `app/src/main/kotlin/com/inscopelabs/buildengine/domain/repository/OperationRepository.kt` (and unimplemented Stage 3.4/3.5 stubs in `OperationRepositoryImpl.kt`): `SCAFFOLD PLACEHOLDER` intentionally preserved because remaining operations are outside Stage 3.3.

### 3. Open Items (Mandatory Disclosure)

1. **OPEN:** Publish cannot run end to end: the capability `order` identifier has no source in the gateway contract or the Task model. PublishOrderResolver fails closed. Needs an operator decision and a contract revision (PR3-003, PR3-027, BD-1.3).
2. **OPEN:** T3-303 'TTL expiry while the prompt is open' is not testable: ConfirmAndMintCapabilityUseCase reads approval time and the expiry check back to back, so ExpiredWhileConfirming cannot occur with a real clock. Needs an operator decision.

### 4. Executed Verification Evidence

| Check / Suite | Scope | Result | Reference |
|---|---|---|---|
| `gradle --no-daemon --console=plain :app:testDebugUnitTest` | All JVM unit tests (58 suites) | **415 passed, 0 failed, 0 skipped** | XML reports in `app/build/test-results/testDebugUnitTest/` |
| `PublishTaskUseCaseTest` | Use-case scope binding, single nonce, head drift/unavailable, declined prompt, invalid target, outcome pass-through | **8 passed, 0 failed** | `app/src/test/.../domain/usecase/PublishTaskUseCaseTest.kt` |
| `PublishBannerPresenterTest` | Distinct refusal/conflict/timeout/uncertain/recovery/pre-send banners, live head detail, `isSuccess` + `auditRef` rules | **7 passed, 0 failed** | `app/src/test/.../ui/publish/PublishBannerPresenterTest.kt` |
| `PublishViewModelTest` | `Ready`, `LoadFailed`, ignored `confirm()` when unpublishable, single submission on double tap, `Finished` banner + `auditRef`, `IN_APP_CONFIRM` prefix forwarding | **10 passed, 0 failed** | `app/src/test/.../ui/publish/PublishViewModelTest.kt` |
| `OperationJournalExecutorTest` | Pre/post-send crash, timeout/lookup recovery, contract outcome mapping, live-head verification | **30 passed, 0 failed** | `app/src/test/.../data/repository/OperationJournalExecutorTest.kt` |
| `OperationLookupWireTest` | `GET /operations/{id}` wire deserialization | **4 passed, 0 failed** | `app/src/test/.../core/network/OperationLookupWireTest.kt` |
| `ArchitectureTest` | Single `.mint(` path, package/secret/shell boundaries | **1 passed, 0 failed** | `app/src/test/.../ArchitectureTest.kt` |
| `gradle --no-daemon --console=plain :app:compileDebugAndroidTestKotlin` | Android Compose UI & flow tests (`PublishConfirmScreenTest`, `PublishProgressScreenTest`, `PublishFlowTest`, `PublishTestFixtures`) | **Compiled cleanly (0 errors)** | `app/src/androidTest/.../ui/publish/` |
| `gradle --no-daemon --console=plain :app:assembleDebug` | Debug APK build | **BUILD SUCCESSFUL** | `app/build/outputs/apk/debug/` |

### 5. Unverified In-Agent (Must Run Outside Sandbox)

- **Compose UI (`androidTest`) runtime execution:** No Android emulator or ADB is present in this environment; `connectedDebugAndroidTest` is **unverified in-agent**.
- **CI `vectors-are-reproducible` check:** Python `cryptography>=43` is not installed in this environment; regenerating `docs/fixtures/vectors` is **unverified in-agent**.
- **Git / PR #66 / GitHub Actions CI verification & push:** `.git` metadata is absent in this sandbox; confirming PR #66 merge SHA on `main`, inspecting GitHub Actions status, and pushing the close-out commit are **unverified in-agent**.
- **Controlled / Live Gateway A publish execution & device biometric/Keystore behavior:** Blocked both on environment availability and on **OPEN Item 1** (`order` identifier source).

---

## Stage 3.4 — Remaining Operations (T3-401 to T3-405; PR3-022 to PR3-027)

- **Status:** `implemented in code; not accepted`
- **Register Traceability:** `PR3-022` (code & JVM tests done; controlled-gateway/SAF runtime open), `PR3-023` (code & JVM tests done; controlled-gateway open), `PR3-024` (code & JVM tests done; controlled-gateway open), `PR3-025` (**OPEN** — blocked on `PR3-003`), `PR3-026` (client DTO/use case/repository done per §3.17; gateway `POST /plans/:id/status` route missing in `gateway/`), `PR3-027` (**DEFERRED** per BD-1.3 Option B)
- **Plan Traceability:** `RELEASE-PLAN.md` Stage 3.4 (`T3-401`, `T3-402`, `T3-403`, `T3-404`, `T3-405`)

### 1. Implementation Artifacts Verified

- **T3-401 / PR3-024 (`HaltUseCase`, `ResumeUseCase`):** Engine-branch (`"engine"`) scoped minting through `ConfirmAndMintCapabilityUseCase`, non-blank & secret-free halt reasons, `ResumeUseCase` failing closed with `MissingRecordedCause` unless `OperationRepository.recordedHaltCause()` decrypts a prior journal halt reason, authoritative `PlanRepository.getStatus()` readback after execution.
- **T3-402 / PR3-023 (`ReconcileUseCase`):** Task-branch scoped reconcile (`OperationType.RECONCILE`), preserves gateway `MERGED:` / `CLOSED:` strings from `OperationOutcome.Succeeded.outcome` into `ReconcileDisposition.Merged` / `ReconcileDisposition.Closed`, performs zero local merges.
- **T3-403 / PR3-022 (`SubmissionArchivePreparer`, `UploadSubmissionUseCase`, `SubmissionDocumentPicker`):** Zero-storage-permission SAF `ActivityResultContracts.OpenDocument` picker; rejects empty/non-ZIP/corrupt archives, >25 MiB payloads (`MAX_UPLOAD_BYTES` matching `gateway/src/config/constants.ts`), path traversal (`..`, `/`, `\`), and denied `engine/` / `plans/` / `.github/` paths; derives SHA-256 and Base64 from one immutable `ByteArray` without mutating the source; `zipBase64` and capabilities never written to Room journal or `Logger`.
- **T3-404 / PR3-025 (`DecideFrameworkUseCase`, `DecisionViewModel`, `DecisionScreen`, `DecideDto`):** Captures required `attribution`, `rationale`, `scope`, `evidence`, `decision`, and `notes`; fails closed with `DecideResult.ContractBlocked` (`contractSchemaSettled = false` default) under `PR3-003`; schema-faithful `DecideRequestDto` when settled (`POST /plans/{id}/decide`).
- **T3-404 / PR3-026 (`SetTaskStatusUseCase`, `PlanStatusTransitionDto`):** Enforces `PENDING -> IN_PROGRESS|DONE` and `IN_PROGRESS -> DONE`, requires completion evidence for `DONE`, uses `SET_IN_PROGRESS` / `SET_DONE`, sends schema-faithful `PlanStatusTransitionRequestDto` (`POST /plans/{id}/status`), and refreshes authoritative task & plan status after verified success.
- **T3-404 / PR3-027 (`CreateWorkOrderUseCase`, `WorkOrder`):** Scaffold markers removed; returns `CreateWorkOrderResult.UnsupportedByContract` under BD-1.3 Option B (engine tooling owns work-order creation).
- **T3-405 (`SyncBranchUseCase`, `GetDoctorReportUseCase`, `DashboardViewModel`, `DashboardScreen`):** Explicit user-triggered `sync` (`POST /sync`) and `doctor` (`GET /doctor`) wired into `DashboardDiagnosticsState` & `DashboardDiagnosticsCard`; zero network calls on `ViewModel` init.

### 2. Scaffold Marker Status (G-09)

- Removed all `SCAFFOLD PLACEHOLDER` / `TODO` markers from Stage 3.4 files (`OperationRepository.kt`, `OperationRepositoryImpl.kt`, `WorkOrder.kt`, `CreateWorkOrderUseCase.kt`, `HaltUseCase.kt`, `ResumeUseCase.kt`, `ReconcileUseCase.kt`, `UploadSubmissionUseCase.kt`, `SubmissionArchivePreparer.kt`, `SetTaskStatusUseCase.kt`, `DecideFrameworkUseCase.kt`, `SyncBranchUseCase.kt`, `GetDoctorReportUseCase.kt`, `DecisionViewModel.kt`, `DecisionScreen.kt`, `DashboardViewModel.kt`, `DashboardScreen.kt`).
- Remaining `SCAFFOLD` / `TODO` markers in `app/src/main/kotlin/` belong strictly to Phase 4 (`SubmissionReviewScreen.kt`, `DecisionHistoryScreen.kt`, `AuditInspectionScreen.kt`, `DeviceManagementScreen.kt`, `HaltResumeScreen.kt`, `ui/theme/{Color,Theme,Type}.kt`) and Phase 5 (`LabScreen.kt`, `LabViewModel.kt`).

### 3. Open Items & Dispositions

1. **OPEN (`PR3-003` / `PR3-025` — `decide` contract reconciliation):** `docs/schemas/decide.schema.json` enforces `additionalProperties: false` over `{plan, task, branch, decision, notes, idempotencyKey, capability}` (forbidding `attribution`, `rationale`, `scope`, `evidence`) and omits `headSha` / `branch` in `DecideAck` required by T3-302 live-head verification. `DecideFrameworkUseCase` and `DecisionScreen` fail closed (`ContractBlocked`) until the contract revision settles this.
2. **OPEN (`PR3-026` — Worker route gap):** Android client implements `POST /plans/{id}/status` matching `docs/gateway-contract.md` §3.17 and `docs/schemas/plan-status-transition.schema.json`, while the protected `gateway/src/routes/` codebase currently exposes only `GET /plans/:id/status` (no `POST /plans/:id/status` handler yet).
3. **OPEN (`order` identifier source — PR3-003 / BD-1.3):** Still unresolved from Stage 3.3; capability minting still requires a contract-defined `order` source before live end-to-end execution.
4. **DEFERRED (`PR3-027` / BD-1.3 Option B):** `CreateWorkOrderUseCase` returns `CreateWorkOrderResult.UnsupportedByContract` and mints/sends nothing.

### 4. Executed Verification Evidence

| Check / Suite | Scope | Result | Reference |
|---|---|---|---|
| `gradle --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug` | Full JVM test suite, `androidTest` Kotlin compilation, debug APK | **BUILD SUCCESSFUL** (`65 suites, 444 tests, 0 failures, 0 errors, 0 skipped`) | `app/build/test-results/testDebugUnitTest/` |
| `HaltAndResumeUseCaseTest` | Engine-branch binding, blank/secret-pattern rejection, journal cause enforcement, declined prompt | **5 passed, 0 failed** | `app/src/test/.../domain/usecase/HaltAndResumeUseCaseTest.kt` |
| `ReconcileUseCaseTest` | Scope binding, `MERGED:`/`CLOSED:` parsing, non-success propagation, invalid branch/submission, head drift | **4 passed, 0 failed** | `app/src/test/.../domain/usecase/ReconcileUseCaseTest.kt` |
| `UploadSubmissionUseCaseTest` | Empty/oversized/corrupt/path-traversal/denied-path pre-checks, SHA-256 vs Base64 immutability, no payload/secret leak in Room journal or logs | **3 passed, 0 failed** | `app/src/test/.../domain/usecase/UploadSubmissionUseCaseTest.kt` |
| `SetTaskStatusUseCaseTest` | Allowed vs forbidden status transitions, `DONE` evidence enforcement, authoritative refresh, schema key check | **5 passed, 0 failed** | `app/src/test/.../domain/usecase/SetTaskStatusUseCaseTest.kt` |
| `DecideFrameworkUseCaseAndViewModelTest` | Required attribution/rationale/scope/evidence validation, default `PR3-003` fail-closed `ContractBlocked`, ViewModel state, settled `DECIDE` minting + `UncertainCompletion`, DTO schema keys | **5 passed, 0 failed** | `app/src/test/.../domain/usecase/DecideFrameworkUseCaseAndViewModelTest.kt` |
| `CreateWorkOrderUseCaseTest` | BD-1.3 Option B deferral (`UnsupportedByContract`) | **1 passed, 0 failed** | `app/src/test/.../domain/usecase/CreateWorkOrderUseCaseTest.kt` |
| `Stage34OperationRepositoryTest` | Journal write/clear of encrypted halt cause & fail-closed decrypt error, reconcile `MERGED:` outcome, status transition, upload wire/head checks | **4 passed, 0 failed** | `app/src/test/.../data/repository/Stage34OperationRepositoryTest.kt` |
| `DashboardViewModelTest` | Zero calls on init + explicit `syncBranch` & `runDoctor` success/failure state transitions | **7 passed, 0 failed** | `app/src/test/.../ui/dashboard/DashboardViewModelTest.kt` |
| `ArchitectureTest` | `.mint(` restricted strictly to `CapabilityMinter.kt` & `ConfirmAndMintCapabilityUseCase.kt` | **1 passed, 0 failed** | `app/src/test/.../ArchitectureTest.kt` |
| `:app:compileDebugAndroidTestKotlin` | `DecisionScreenTest` Compose test + Stage 3.3 UI test suite | **Compiled cleanly (0 errors)** | `app/src/androidTest/.../ui/decision/DecisionScreenTest.kt` |
| `:app:assembleDebug` | Debug APK assembly | **BUILD SUCCESSFUL** | `app/build/outputs/apk/debug/` |

### 5. Unverified In-Agent (Must Run Outside Sandbox)

- **Compose UI (`androidTest`) runtime execution:** `DecisionScreenTest` (and Stage 3.3 UI tests) compiled cleanly, but `connectedDebugAndroidTest` is **unverified in-agent** (no Android emulator/ADB).
- **Android runtime Keystore, Room, SAF picker & WorkManager:** Real `AndroidKeystoreJournalCipher`, biometric/device-credential prompt, SAF `OpenDocument`, and WorkManager recovery are **unverified in-agent**.
- **Controlled / Live Gateway A execution:** Executing upload, reconcile, halt, resume, status transition, decide, sync, and doctor against a live or controlled Gateway A Worker is **unverified in-agent** (also gated on Open Items 1–3 above).
- **CI workflows & vector script:** GitHub Actions, `.git` diff/push, and `generate_vectors.py` (`cryptography` module absent) are **unverified in-agent**.
