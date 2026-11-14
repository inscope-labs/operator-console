# Phase 3 Evidence Dossier

> **Scope notice:** This document contains the **Stage 3.3** evidence section only. Remaining Phase 3 stages (3.4 remaining operations, full Gate 3 checklist, and overall Phase 3 acceptance) are not closed by this document. Do **not** claim Gate 3 or on-device behavior from this record.

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
