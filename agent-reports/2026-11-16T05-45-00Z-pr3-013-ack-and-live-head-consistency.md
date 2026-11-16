# PR3-013 — Acknowledgement and Live-Head Verification Consistency Report

- **PR3 ID:** `PR3-013` (*Make acknowledgement and live-head verification consistent*; `docs/release/PHASE-3-REMEDIATION-REGISTER.md` rev 1.2, Critical, `[PLAN]`, Workstream B)
- **Traceability:** `T3-302`, `T3-205`, `I-06`
- **Timestamp (UTC):** `2026-11-16T05:45:00Z`
- **Commit SHA:** `unverified — no .git metadata in sandbox` (proposed commit subject for push: `PR3-013: unify synchronous ack and lookup verification with scope, SHA, and monotonicity checks`)
- **Status / Closure:** **JVM-tested** (`implemented` in code; `JVM-tested` locally; CI run on pushed SHA unverified; Android-runtime/integration evidence not produced; **NOT accepted** — operator acceptance pending).

---

## 1. Files Changed

Only non-protected Android application, unit-test, status, and report paths were touched:

1. `app/src/main/kotlin/com/inscopelabs/buildengine/data/repository/OperationRepositoryImpl.kt` — Unified synchronous acknowledgement and `GET /operations/{id}` completed-status handling into `verifyAndFinalizeCompletedOperation`, added scope/ID/40-char SHA/contradictory-state checks, fixed ack decryption-failure overwrite, caught `GatewayNotConfiguredException` in `verifyLiveHead`, and enforced monotonic terminal state guards.
2. `app/src/test/kotlin/com/inscopelabs/buildengine/data/repository/OperationJournalExecutorTest.kt` — Updated incomplete synthetic `GatewayAckDto` stubs to include `operation`/`branch` per contract schemas and added 7 negative/fault/monotonicity unit tests covering `PR3-013` requirements on both ack and lookup paths.
3. `docs/STATUS.md` — Narrowly updated only the `T3-205` and `T3-302` rows in the Phase 3 detail table to record `PR3-013` implementation and local JVM test execution while keeping CI, runtime, integration, and acceptance honestly marked open/unverified.
4. `agent-reports/2026-11-16T05-45-00Z-pr3-013-ack-and-live-head-consistency.md` — Created this `PR3-013` verification report.

### Protected / Excluded Paths Confirmation
- **No protected files** modified (`proguard-rules.pro`, `.github/workflows/*.yml`, `AndroidManifest.xml`, `app/build.gradle.kts`, `version.properties`, `build-logs/**`, `gradle/libs.versions.toml`, `network_security_config.xml`).
- **No** `gateway/**`, Phase 4+ UI, or release machinery changes.

---

## 2. PR3-013 Acceptance Criteria Assessment

| Criterion | Status | Code & Test Evidence |
|---|---|---|
| **Work:** Unify synchronous acknowledgement handling and recovery lookup so both apply the same terminal-success criteria. | **MET** | Both `handleWriteAcknowledgement` (`CompletionSource.ACK`, `OperationRepositoryImpl.kt:329-339`) and `mapLookupDtoToOutcome` for `STATUS_COMPLETED` (`CompletionSource.LOOKUP`, `OperationRepositoryImpl.kt:727-736`) delegate to the single shared method `verifyAndFinalizeCompletedOperation` (`OperationRepositoryImpl.kt:356-521`). |
| Nonblank `auditRef`/`headSha` alone is insufficient to report success. | **MET** | `verifyAndFinalizeCompletedOperation` always validates operation, branch, full 40-char hex `headSha`, and live `gateway.branchHead(effectiveBranch)` match (`OperationRepositoryImpl.kt:443-488`) before persisting `TERMINAL_SUCCEEDED` or returning `OperationOutcome.Succeeded`. |
| The operation acknowledgement/lookup must match the journalled operation ID and expected operation/scope. | **MET** | Lookup enforces `lookup.id.trim() == key` (`OperationRepositoryImpl.kt:670-681`); both paths enforce case-insensitive match of `reportedOperation` against `decryptedRecord.intent.operation.name` (`OperationRepositoryImpl.kt:375-390, 683-695`) and non-contradiction of `reportedBranch` against `decryptedRecord.intent.branch` (`OperationRepositoryImpl.kt:392-404, 697-709`), plus missing ack branch fails closed to `UncertainCompletion` (`OperationRepositoryImpl.kt:431-441`). Verified by `OperationJournalExecutorTest` lines 1080-1233. |
| Required branch and full head SHA are checked against the live branch head. | **MET** | `verifyAndFinalizeCompletedOperation` validates `FULL_SHA_REGEX` (`^[0-9a-fA-F]{40}$`) on `headSha` (`OperationRepositoryImpl.kt:443-453`) and invokes `verifyLiveHead` (`OperationRepositoryImpl.kt:523-560`), which checks `live.branch` non-contradiction (`549-553`), `FULL_SHA_REGEX` on `live.headSha`, and case-insensitive equality (`554-559`). Verified by `OperationJournalExecutorTest` lines 1125-1164 and 1253-1294. |
| Head drift, missing audit evidence, malformed lookup, contradictory state or unavailable verification results in `UncertainCompletion`, `Conflict` or `RecoveryRequired`, never success. | **MET** | Missing `auditRef`/`headSha` -> `UncertainCompletion` (ack) or `RecoveryRequired` (lookup) (`406-429`); short/malformed `headSha` -> `RecoveryRequired` (`443-453`); drifted/contradicted/HTTP-4xx/malformed live head -> `UncertainCompletion` (`509-519`); network/5xx/`GatewayNotConfiguredException` on live head -> `UncertainCompletion` / `TransientRetry` (`489-508`, `526-544`); lookup `STATUS_COMPLETED` with non-blank `code` -> `RecoveryRequired` (`716-726`); ack decryption failure returns `RecoveryRequired` without overwriting `RECOVERY_REQUIRED` to `UNCERTAIN` (`309-313`, `983-1006`). Verified by `OperationJournalExecutorTest` lines 861-925, 1080-1294. |
| The result is monotonic: a terminal refusal/conflict cannot later be overwritten as success without an explicit, contract-defined reconciliation rule. | **MET** | `lookupOperation` (`105-107`), `verifyAndFinalizeCompletedOperation` (`367-370`), `mapLookupDtoToOutcome` (`664-667`), `markUncertain` (`896`), `markUncertainWithResult` (`912`), `markTerminalSucceeded` (`935-939`), `markTerminalRefusal` (`963`), and `markRecoveryRequired` (`988-991`) guard `TERMINAL_OUTCOME_STATES` / `NON_SUCCEEDED_TERMINAL_STATES` against overwrite. Note: `TERMINAL_SUCCEEDED` cannot be overwritten by refusal/uncertainty, and `TERMINAL_REFUSED` / `TERMINAL_CONFLICT` / `RECOVERY_REQUIRED` cannot transition to `TERMINAL_SUCCEEDED`. Verified by `OperationJournalExecutorTest` lines 1297-1346. |

---

## 3. Verification Commands & Real Results

Executed locally in `/app/applet` after all code, test, and documentation edits:

1. **Full JVM Debug Unit & Architecture Test Suite:**
   ```text
   gradle :app:testDebugUnitTest --console=plain
   ```
   - Outcome: `BUILD SUCCESSFUL in 16s`
   - Aggregate report (`app/build/reports/tests/testDebugUnitTest/index.html`): **495 tests run, 0 failures, 0 errors, 0 skipped (100% successful)**
   - Key suite counts from `app/build/test-results/testDebugUnitTest/TEST-*.xml`:
     - `com.inscopelabs.buildengine.data.repository.OperationJournalExecutorTest`: **37 tests, 0 failures, 0 errors, 0 skipped** (includes 7 new `PR3-013` tests)
     - `com.inscopelabs.buildengine.data.repository.Stage34OperationRepositoryTest`: **4 tests, 0 failures, 0 errors, 0 skipped**
     - `com.inscopelabs.buildengine.core.network.OperationLookupWireTest`: **4 tests, 0 failures, 0 errors, 0 skipped**
     - `com.inscopelabs.buildengine.domain.usecase.common.VerifyBranchHeadUseCaseTest`: **6 tests, 0 failures, 0 errors, 0 skipped**
     - `com.inscopelabs.buildengine.domain.usecase.PublishTaskUseCaseTest`: **8 tests, 0 failures, 0 errors, 0 skipped**
     - `com.inscopelabs.buildengine.ui.publish.PublishViewModelTest`: **10 tests, 0 failures, 0 errors, 0 skipped**
     - `com.inscopelabs.buildengine.domain.usecase.DecideFrameworkUseCaseAndViewModelTest`: **5 tests, 0 failures, 0 errors, 0 skipped**
     - `com.inscopelabs.buildengine.ArchitectureTest`: **7 tests, 0 failures, 0 errors, 0 skipped**
     - `com.inscopelabs.buildengine.architecture.ArchitectureScannerTest`: **12 tests, 0 failures, 0 errors, 0 skipped**

2. **Full Android Debug Build Compilation (`compile_applet`):**
   - Outcome: `Build succeeded - the applet is compiled`

### Evidence NOT Produced (and Why)
- **Android-runtime / instrumented Room + Keystore + UI tests (`androidTest`):** Not run (no Android emulator or ADB available in sandbox; covered separately by `PR3-040` / `PR3-041`).
- **Controlled-gateway integration tests (`PR3-042`):** Not run (all repository/executor tests ran against JVM fakes `FakeGatewayApi`, `InMemoryOperationJournalDao` / `FakeOperationJournalDao`, and `RecordingFakeCipher`; no operator-named controlled gateway environment in sandbox).
- **CI run on pushed SHA:** Unverified (no `.git` directory or remote push from this sandbox).

---

## 4. Invariants Preserved

- **I-05 (Idempotent write & no blind retry):** Shared path never triggers a write retry; lookup/live-head checks are strictly `GET`.
- **I-06 (No false success):** Terminal `Succeeded` requires non-blank `auditRef`, valid 40-char `headSha`, matching operation/scope/ID, and matching live `branchHead` check. `DecideAckDto` omitting `headSha` continues to resolve to `UncertainCompletion`.
- **I-07 (Fail closed):** Operation/ID/branch mismatches, non-40-char SHAs, contradictory `completed`+`code` responses, and cipher decryption errors fail closed to `RecoveryRequired` (`RECOVERY_REQUIRED`) or `UncertainCompletion` (`UNCERTAIN`).
- **I-08 (Zero secrets in journal/logs):** Only non-secret operation/branch/SHA/auditRef metadata and error codes enter the cipher or logs; capabilities, signatures, and nonces remain excluded (`OperationJournalExecutorTest` lines 813-856, `ArchitectureTest` Rules F1/F2).
- **I-10 (Contract fidelity):** No gateway routes, fields, or DTO properties were invented; existing `GatewayAckDto`, `OperationLookupDto`, `BranchHeadDto`, `PlanStatusTransitionAckDto`, and `DecideAckDto` schemas remain strictly unchanged.
- **I-12 (One minting path & journal containment):** Gateway write calls inside `OperationRepositoryImpl` remain enclosed in `executeOperation` (`ArchitectureTest` Rule D passes with 0 violations).

---

## 5. Open Blockers / Ambiguities / Follow-ups for Claude & Operator

- **PR3-003 questions 2 & 3 (Claude docs PR / contract traceability):** What live-head verification means on recovery for `decide` (whose `decide.schema.json` omits `headSha` and `branch`, keeping synchronous `decide` at `UncertainCompletion`) and `POST /plans/{id}/status` (`PR3-026` also awaiting gateway route per ADR 0014 D3), and how `GET /operations/{id}` reports them, remain open in `PR3-003`.
- **PR3-042 (Operator + Gateway):** Controlled-gateway integration testing of `GET /operations/{id}` + live `GET /branches/{name}/head` requires an operator-named test gateway environment.
- **CI confirmation (Claude / Operator):** Push changes and confirm `android-ci` passes on the pushed SHA.
- No protected-file edits are needed by `PR3-013`.

---

## 6. Recommended Next Eligible Item

`PR3-011`
