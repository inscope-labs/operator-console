# PR3-011 — Enforce Atomic Intent-Before-Dispatch

- **PR3 ID:** `PR3-011` (`RELEASE-PLAN.md` Stage 3.2 / `T3-202`; `I-03`; `docs/release/PHASE-3-REMEDIATION-REGISTER.md` rev 1.2)
- **Timestamp (UTC):** `2026-11-16T07:22:09Z`
- **Commit SHA:** `unverified` (no `.git` directory in AI Studio sandbox; workspace files audited directly)
- **Status Label (per plan vocabulary):** `implemented` / `JVM-tested (local)` (CI unverified until pushed; Android-runtime/Room SQLite execution **not produced**). **NOT ACCEPTED** — acceptance remains exclusively with the operator (John) and architect review (Claude).

---

## 1. Selection & Eligibility

- **Ownership (Appendix B):** `PR3-011` is `[AIS]` / Stage 3.2 (Critical priority).
- **Dependencies:** `PR3-010` (GCM associated data) and `PR3-013` (ack/live-head consistency) were already completed in workspace.
- **Unblocked & Narrowly Scoped:** Purely client-side Room DAO + `OperationRepositoryImpl` journal correctness; requires zero gateway/contract/schema changes and zero edits to protected paths.
- **Prior Gaps Identified on Main Before Edits:**
  1. `OperationJournalState` had no state-transition validation (`allowedNextStates` / `canTransitionTo`); backwards transitions (e.g., `SENT -> INTENT_RECORDED`, `ACKNOWLEDGED -> SENT`) or state-skipping jumps (e.g., `INTENT_RECORDED -> TERMINAL_SUCCEEDED`) were unchecked.
  2. `OperationJournalDao` exposed an unvalidated `@Update suspend fun update(entry)` returning `Unit` (ignoring `0`-row updates) and had no `@Transaction` atomic commit-if-no-active method or compare-and-set (`WHERE idempotencyKey = :key AND state = :expectedFromState`) update.
  3. `OperationRepositoryImpl.recordAndExecute` performed separate `findByIntentFingerprintAndStates` + `insert` calls, `dispatchRecordedEntry` did not verify the committed DB row or CAS `INTENT_RECORDED -> SENT` result before invoking `send(key)`, `handleWriteAcknowledgement` called `journalDao.update(...)` without error handling or monotonicity fallback, `markRecoveryRequired` only checked `TERMINAL_REFUSED`/`TERMINAL_CONFLICT` rather than all terminal states, and concurrent coroutines waiting behind `intentLocks` after a fast `TERMINAL_SUCCEEDED` could re-run the same intent instead of coalescing.

---

## 2. Files Added or Changed

| File | Change |
|---|---|
| `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/OperationJournalEntity.kt` | Added `allowedNextStates()`, `canTransitionTo()`, `isTerminal()`, and `requireValidTransition()` enforcing the forward-only 8-state machine on `OperationJournalState` (`PR3-010` AAD/cipher code preserved intact). |
| `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/OperationJournalDao.kt` | Added `@Transaction suspend fun commitIntentIfNoActive(...)`, `@Query suspend fun updateStateIfCurrent(...): Int` (atomic CAS guarded by `expectedFromState`), and `@Transaction suspend fun transitionState(...): Boolean` (calls `requireValidTransition` before CAS SQL); removed unvalidated `@Update suspend fun update(...)`. |
| `app/src/main/kotlin/com/inscopelabs/buildengine/data/repository/OperationRepositoryImpl.kt` | Enforced atomic `commitIntentIfNoActive`, pre-dispatch DB existence & state check + `INTENT_RECORDED -> SENT` CAS confirmation before `send(key)`, concurrent `InFlightBatch` coalescing for overlapping coroutines on the same intent, `lookupOperation` lock serialization, unified `transitionEntity(...)` across all state transitions, and terminal monotonicity preservation in `handleWriteAcknowledgement` (`PR3-010` AAD & `PR3-013` lookup/live-head logic preserved). |
| `app/src/test/kotlin/com/inscopelabs/buildengine/testutil/JournalTestFixtures.kt` | Implemented atomic `Mutex`-guarded `commitIntentIfNoActive`, `updateStateIfCurrent`, and `transitionState` on `InMemoryOperationJournalDao`. |
| `app/src/test/kotlin/com/inscopelabs/buildengine/data/local/OperationJournalStateTransitionTest.kt` *(new)* | Added 3 JVM tests covering the exhaustive 8x8 state-transition matrix, DAO `transitionState` rejection of backwards/skipped jumps without mutating rows, and DAO `commitIntentIfNoActive` initial-state validation + concurrent deduplication. |
| `app/src/test/kotlin/com/inscopelabs/buildengine/data/repository/OperationJournalExecutorTest.kt` | Updated `FakeOperationJournalDao` with `Mutex`-guarded atomic `commitIntentIfNoActive`/`transitionState`, fault-injection hooks, and transition log recording; added 8 `PR3-011` JVM tests (failed `SENT` commit, 0-row CAS failure, unpersisted/non-durable DAO record, post-`SENT` pre-wire crash recovery, ordered transition verification, 10-way concurrent burst, dual-repository cross-instance DAO race, and `ACKNOWLEDGED` DAO failure). |
| `docs/STATUS.md` | Updated only the `Stage 3.2` and `T3-202` rows to record `PR3-011` implementation and local JVM test counts while keeping CI, Android-runtime, and acceptance honest (`no`). |
| `agent-reports/2026-11-16T07-22-09Z-pr3-011-atomic-intent-before-dispatch.md` *(new)* | This report. |

### Protected / Forbidden Path Confirmation

- **Untouched protected paths:** `.github/workflows/*.yml`, `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `version.properties`, `build-logs/**`, `gradle/libs.versions.toml`, `network_security_config.xml`.
- **Untouched out-of-scope trees:** `gateway/**`, `RELEASE-PLAN.md`, `docs/gateway-contract.md`, `docs/schemas/**`, `docs/adr/**`, `app/schemas/.../1.json`.
- **Room KSP verification:** Inspected generated `app/build/generated/ksp/debug/kotlin/com/inscopelabs/buildengine/data/local/OperationJournalDao_Impl.kt`; Room generated `performInTransactionSuspending(__db)` for `commitIntentIfNoActive` and `transitionState` and `UPDATE ... WHERE idempotencyKey = ? AND state = ?` returning `getTotalChangedRows(_connection)` for `updateStateIfCurrent`. Schema `1.json` hash (`d11ab474bec86accd0ce365850718e5c`) is unchanged; no migration required.

---

## 3. Acceptance Criteria Mapping

| # | Acceptance Criterion (`PR3-011`) | Verification Scope & Outcome |
|---|---|---|
| 1 | A failed journal transaction results in zero network dispatches. | **MET (JVM-tested with fakes):** `journal insert failure before send aborts and sends nothing`, `PR3-011 failed SENT transition transaction results in zero network dispatches and logs fault`, and `PR3-011 CAS returning zero rows on SENT transition results in zero network dispatches` verify `sendCalls == 0`, fail-closed `RecoveryRequired`, and fault logs without secrets. |
| 2 | The executor cannot dispatch an operation without a committed journal record. | **MET (JVM-tested with fakes):** `dispatchRecordedEntry` requires `INTENT_RECORDED`, re-reads `findByKey(key)` before and after `transitionState(INTENT_RECORDED -> SENT)`, and aborts with zero dispatches if absent or mismatched. Tested by `PR3-011 executor cannot dispatch without a committed journal record` (`unpersistingDao` returns uncommitted entity with `findByKey == null` -> `sendCalls == 0`). |
| 3 | A crash after journal commit but before dispatch is recoverable without creating a second operation identity. | **MET (JVM-tested with fakes):** Both post-`INTENT_RECORDED` crash (`crash between journal write and send sends nothing and leaves recoverable entry`) and post-`SENT`-commit pre-wire crash (`PR3-011 crash after SENT commit before send recovers with same identity and zero second write`) reuse the single `idempotencyKey`, keep 1 journal row, and finish without duplicate writes. |
| 4 | State transitions are validated and cannot move backwards or skip required states. | **MET (JVM-tested):** `OperationJournalStateTransitionTest` asserts all 64 pairs of the 8x8 state matrix and proves `OperationJournalDao.transitionState` throws `IllegalArgumentException` on backwards/skipped/post-terminal transitions without mutating stored rows; `PR3-011 successful operation records ordered forward state transitions with zero skips` verifies `[INTENT_RECORDED -> SENT, SENT -> ACKNOWLEDGED, ACKNOWLEDGED -> TERMINAL_SUCCEEDED]`. |
| 5 | Concurrent taps/coroutines for the same logical intent cannot create duplicate dispatches. | **MET (JVM-tested with fakes):** `concurrent double tap for one intent`, `PR3-011 high concurrency taps for same logical intent produce single journal row and single dispatch` (10 overlapping coroutines -> 1 journal row, 1 write call), `PR3-011 concurrent dispatches across separate repository instances share atomic DAO protection` (2 independent `OperationRepositoryImpl` instances sharing one DAO -> 1 journal row, 1 write call), and `DAO commitIntentIfNoActive ... deduplicates active fingerprint` (8 concurrent coroutines -> 1 row, 1 winning CAS). |

---

## 4. Verification Results

Commands executed locally in `/app/applet`:
- `compile_applet` — **Build succeeded** (Room KSP + Kotlin debug compilation passed).
- `gradle :app:testDebugUnitTest --console=plain` — **BUILD SUCCESSFUL** (`523` tests run, `0` failures, `0` skipped, `100%` pass rate across all test suites).

Suite-level results read from `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/test-results/testDebugUnitTest/TEST-*.xml`:
- `OperationJournalExecutorTest`: **45 / 45 passed** (`0` failed, `0` skipped)
- `OperationJournalStateTransitionTest`: **3 / 3 passed** (`0` failed, `0` skipped)
- `JournalFieldCipherAssociatedDataTest`: **17 / 17 passed** (`0` failed, `0` skipped)
- `IdempotencyKeyGeneratorTest`: **4 / 4 passed** (`0` failed, `0` skipped)
- `Stage34OperationRepositoryTest`: **4 / 4 passed** (`0` failed, `0` skipped)
- `ArchitectureTest`: **7 / 7 passed** (`0` failed, `0` skipped)
- `BuildConfigStage31Test`: **4 / 4 passed** (`0` failed, `0` skipped)

### Evidence NOT Produced (Explicitly Labeled)
- **Android-runtime / SQLite instrumentation (`PR3-040`):** **NOT PRODUCED.** No Android emulator, ADB, Robolectric, or `androidx.room:room-testing` dependency is present in this environment (and `app/build.gradle.kts` / `libs.versions.toml` are protected). All DAO/executor runtime tests executed on JVM against `InMemoryOperationJournalDao` / `FakeOperationJournalDao`, while real Room SQL and `@Transaction` wrappers were verified via KSP code generation (`OperationJournalDao_Impl.kt`). Real on-device SQLite transaction/rollback and `AndroidKeyStore` execution stay open under `PR3-040`.
- **Controlled gateway integration (`PR3-042`):** **NOT PRODUCED.** Out of scope.
- **CI confirmation on pushed commit:** **NOT PRODUCED** (`no .git` in sandbox; pending push & GitHub Actions `android-ci`).

---

## 5. Invariants & Residual Risks

- **`I-03` (Journal before send):** Strengthened. Write dispatch now requires an atomic `commitIntentIfNoActive` commit, explicit `findByKey` verification, validated `INTENT_RECORDED -> SENT` CAS (`rows == 1`), and post-CAS `SENT` confirmation prior to invoking `send(key)`.
- **`I-05` (No blind retry) & `I-08` (Fail closed):** Preserved/strengthened. CAS losses against another concurrent advance never issue a second write, and any DB/crypto/state error fails closed to `RecoveryRequired` without emitting capability secrets, nonces, or signatures in logs.
- **`I-04`, `I-06`, `I-10`:** Preserved (`ArchitectureTest` 7/7 green; wire DTOs/endpoints unchanged).
- **Residual note:** `InMemoryOperationJournalDao` and `FakeOperationJournalDao` use explicit `Mutex` locking to model SQLite's serialized write transaction in JVM unit tests; actual Android SQLite `BEGIN IMMEDIATE` transaction serialization under multi-thread contention still requires `PR3-040` instrumentation tests on Android runtime.

---

## 6. Recommended Next Eligible Item

- **`PR3-025`** — Align `DecideFrameworkUseCase` / `DecisionViewModel` with ADR 0014 D2's documented reduction (`decision` + `notes`, removing pre-D2 `ContractBlocked` default), or **`PR3-012`** audit/hardening for key-vs-fingerprint collision conflict handling on explicit lookup/replay boundaries (`T3-204`).
