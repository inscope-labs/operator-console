# Agent Report — `PR3-012` Idempotency & Duplicate-Intent Audit

- **Date (UTC):** `2026-11-16T17:15:00Z`
- **Task:** `PR3-012` (*Complete idempotency and duplicate-intent protection*; traceability `T3-204`, `I-05`, Gate 3)
- **Rule:** **Tests and docs only.** Production defects discovered during the audit are recorded below as findings and **not patched on `main`**.
- **Verification Ceiling:** **JVM-tested** (`:app:testDebugUnitTest`); no Android emulator/ADB, `AndroidKeyStore`, Room SQLite WAL runtime (`PR3-040`), `WorkManager` runtime (`PR3-041`), or controlled Gateway A server (`PR3-042`) is available in this sandbox. Operator acceptance is not claimed.

---

## 1. Files Created & Updated

- **Created:** `app/src/test/kotlin/com/inscopelabs/buildengine/data/repository/IdempotencyAndFingerprintTest.kt` (9 tests)
- **Created:** `agent-reports/2026-11-16T17-15-00Z-pr3-012-idempotency-audit.md` (this report)
- **Updated:** `docs/STATUS.md` (Stage 3.2 and `T3-204` rows only)

---

## 2. Acceptance-Bullet Audit (`PR3-012`)

| # | Acceptance Bullet | Pre-existing Coverage Audited | Gaps Identified | New Coverage Added (`IdempotencyAndFingerprintTest`) | Status |
|---|---|---|---|---|---|
| **1** | Same user intent reuses its existing key while in flight or unresolved | `OperationJournalExecutorTest` (`repeated taps while operation is in flight reuse one key and never send a second write`, `concurrent double tap for one intent reuses one key and executes single write`, `PR3-011 high concurrency taps...`, `PR3-011 concurrent dispatches across separate repository instances...`), `OperationJournalStateTransitionTest` (`DAO commitIntentIfNoActive... deduplicates active fingerprint`) | Unresolved `ACKNOWLEDGED` state reuse was not explicitly exercised on duplicate execute; terminal-state (`TERMINAL_CONFLICT`) contrast (generating a fresh key on retry) lacked a direct test | `bullet 1 unresolved ACKNOWLEDGED intent reuses existing key via lookup and terminal conflict issues fresh key on retry` | **Covered (JVM)** |
| **2** | Materially different operation/scope produces a different key | `OperationJournalExecutorTest` (`repeated taps...` only varied `task` `"10008"` vs `"10099"`) | Zero direct unit tests on `OperationIntent.fingerprint()`; no length-prefix boundary collision tests; no multi-operation/moved-head/`zipSha256` distinct-key checks while a prior intent stays unresolved (`UNCERTAIN`); `decide` omits `notes` from `OperationIntent` (**Finding F1**) | `bullet 2 OperationIntent fingerprint is deterministic and sensitive to every field and boundary`; `bullet 2 materially different operation, head, submission, or zipSha256 gets distinct keys while prior intent is unresolved`; `bullet 2 finding DECIDE omits notes from OperationIntent so differing notes collide while unresolved` (pins **F1**) | **Partial — Finding F1 recorded** |
| **3** | Reusing a key with a different fingerprint is treated as a conflict and never dispatched as a new write | `OperationJournalExecutorTest` (`lookup refused with every section 4_3 conflict code maps to Conflict` covered `GET /operations/{id}` with `IDEMPOTENCY_KEY_REUSED`) | No wire-level `POST` HTTP `409 IDEMPOTENCY_KEY_REUSED` test, no lookup monotonicity assertion for `IDEMPOTENCY_KEY_REUSED`, and no local `@PrimaryKey` key-reuse collision test (revealing **Finding F2**) | `bullet 3 gateway 409 IDEMPOTENCY_KEY_REUSED on write maps to Conflict TERMINAL_CONFLICT with zero retries`; `bullet 3 lookup refused with IDEMPOTENCY_KEY_REUSED maps to Conflict and remains monotonic`; `bullet 3 finding local key reuse with different fingerprint sends nothing and preserves existing row` (pins **F2**) | **Partial — Finding F2 recorded** |
| **4** | Process restart does not generate a new key for an unresolved intent | `OperationJournalExecutorTest` (`crash between journal write and send...`, `crash between send and ack...`) tested `INTENT_RECORDED` and `SENT` with a single repository/keyGenerator instance | Did not swap in differently seeded `IdempotencyKeyGenerator`s across restarts, nor test restarts across all 4 unresolved states (`INTENT_RECORDED`, `SENT`, `ACKNOWLEDGED`, `UNCERTAIN`) including re-tap + repeated recovery | `bullet 4 process restart across every unresolved state keeps original key and never creates a new key` | **Covered (JVM)** |
| **5** | Idempotency keys are not confused with capability nonces | `IdempotencyKeyGeneratorTest` (`idempotency key is independent of and never equal to capability nonce` with separate `Random` seeds), `DeviceNonceSourceTest` (6 tests), `OperationJournalExecutorTest` (hardcoded `SECRET_NONCE` leakage test) | No test sharing one `SecureRandomSource` (mirroring `CryptoModule` + `DataModule`), inspecting decoded wire `capability` (`ctxop-v1` field 10 `nonce`) vs `idempotencyKey`, preserving `key1` across capability re-minting (`nonce2 != nonce1`), or proving `GET /operations/{id}` uses `idempotencyKey` and never any nonce | `bullet 5 idempotency key and capability nonce remain distinct on shared CSPRNG, wire, re-mint, and lookup` | **Covered (JVM)** |

---

## 3. Production Defects Recorded as Findings (Not Patched on `main`)

1. **Finding F1 (`OperationRepositoryImpl.decide` omits `notes` from `OperationIntent`):**
   - `docs/gateway-contract.md` §1.6 defines the request fingerprint as SHA-256 over the operation and every request field except `capability` and `zipBase64`.
   - `DecideRequestDto` includes `notes` (rationales per ADR 0014 D2), but `OperationIntent` (`OperationJournalEntity.kt` lines 122–174) has no `notes` field and `OperationRepositoryImpl.decide` (`OperationRepositoryImpl.kt` lines 1503–1510) constructs `OperationIntent` without `notes`.
   - **Impact:** Two `DECIDE` intents with identical `(plan, task, branch, expectedHead, decision)` and materially different `notes` produce the same `intentFingerprint`. While the first intent is unresolved, the second attempt collides locally and suppresses dispatch into lookup of the first key instead of minting a distinct key (and if replayed on a gateway computing §1.6 fingerprints across `notes`, would mismatch).
   - **Pinned by:** `IdempotencyAndFingerprintTest.`bullet 2 finding DECIDE omits notes from OperationIntent so differing notes collide while unresolved``.

2. **Finding F2 (Local key-reuse collision returns `OperationOutcome.RecoveryRequired` instead of `Conflict`):**
   - `IdempotencyKeyGenerator` does not check `operation_journal` history before emitting a 128-bit key (unlike `DeviceNonceSource`, which maintains an in-memory `issued` set).
   - If `keyGenerator.generate()` returns a key that already exists in Room under a different `intentFingerprint`, `OperationJournalDao.commitIntentIfNoActive` (`OperationJournalDao.kt` line 35, `OnConflictStrategy.ABORT`) aborts the insert.
   - `OperationRepositoryImpl.recordAndExecute` (`OperationRepositoryImpl.kt` lines 281–288) catches `Exception` generically and returns `OperationOutcome.RecoveryRequired(idempotencyKey = key)` without distinguishing a primary-key collision (`IDEMPOTENCY_KEY_REUSED`). Wait-and-send safety *is* preserved (`0` writes sent, original row untouched), but the surfaced outcome is `RecoveryRequired` rather than `Conflict(code = CODE_IDEMPOTENCY_KEY_REUSED)`.
   - **Pinned by:** `IdempotencyAndFingerprintTest.`bullet 3 finding local key reuse with different fingerprint sends nothing and preserves existing row``.

3. **Finding F3 (`ErrorMapper.GATEWAY_CODES` omits `IDEMPOTENCY_KEY_REUSED`, `IDEMPOTENCY_KEY_INVALID`, `OPERATION_NOT_FOUND`):**
   - Already tracked by `ContractConformanceTest.ERROR_CODE_RATCHET_BASELINE`. `OperationRepositoryImpl` keeps its own `CONFLICT_CODES` and `KNOWN_GATEWAY_CODES` sets (`OperationRepositoryImpl.kt` lines 1582–1597) containing all three codes so repository outcomes map `IDEMPOTENCY_KEY_REUSED` correctly, whereas `ErrorMapper.stateFor("IDEMPOTENCY_KEY_REUSED", 409)` alone returns `GatewayFailureState.UNKNOWN`.

4. **Finding F4 (`OperationRepositoryImpl.handleExistingInFlight` does not cross-check `decrypted.intent.fingerprint() == existing.intentFingerprint`):**
   - `OperationRepositoryImpl.handleExistingInFlight` (`OperationRepositoryImpl.kt` line 213) decrypts `existing` without asserting that the decrypted intent fields re-hash to `existing.intentFingerprint` (`intentFingerprint` is not part of `JournalColumn` / GCM AAD).

---

## 4. Preserved Constraints

- `OperationJournalExecutorTest.kt` was **not edited**.
- No production Kotlin sources were modified (tests and docs only).
- No protected paths (`.github/workflows/*.yml`, `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `version.properties`, `build-logs/**`, `gradle/libs.versions.toml`, `network_security_config.xml`) or `gateway/**` were touched.

---

## 5. Verification Executed

- `gradle --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest --tests "com.inscopelabs.buildengine.data.repository.IdempotencyAndFingerprintTest"`: **BUILD SUCCESSFUL** — `IdempotencyAndFingerprintTest`: **9 tests, 0 failures, 0 skipped**.
- `gradle --no-daemon --console=plain -Dkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest`: **BUILD SUCCESSFUL** — **557 tests, 0 failures, 0 skipped** (`OperationJournalExecutorTest`: 45/45; `FaultMatrixTest`: 9/9; `IdempotencyAndFingerprintTest`: 9/9; `IdempotencyKeyGeneratorTest`: 4/4; `DeviceNonceSourceTest`: 6/6; `OperationJournalStateTransitionTest`: 3/3; `JournalFieldCipherAssociatedDataTest`: 17/17; `OperationLookupWireTest`: 4/4).
- Android runtime (`PR3-040`/`PR3-041`) and live/controlled Gateway A integration (`PR3-042`): **NOT MET** (no emulator, ADB, or Gateway A instance in sandbox).
