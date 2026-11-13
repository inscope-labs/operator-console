# Stage 3.2 Report — Journal, Executor, and Outcome Model

- **Timestamp (UTC):** `2026-11-13T07:54:31Z`
- **Plan Reference:** `RELEASE-PLAN.md` (Revision 1.2), Phase 3, Stage 3.2 (`T3-201` through `T3-205`)
- **Decision Basis:** ADR 0009 (encrypted Room + WorkManager), BD-3.2, ADR 0013 / BD-1.7 (client idempotency key and `GET /operations/{id}` lookup), `docs/gateway-contract.md` (Revision 1.1)

---

## 1. Pre-Flight Verification

1. Checked `gradle/libs.versions.toml` and `app/build.gradle.kts`: `androidx-room-runtime` (2.8.5), `androidx-room-ktx` (2.8.5), `androidx-room-compiler` (2.8.5 via `ksp`), `androidx-work-runtime-ktx` (2.11.2), and `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` are present. No dependencies were added or modified.
2. Verified `docs/gateway-contract.md` is at Revision 1.1 with §1.6, §3.19, §4.3, and §4.1 error codes `IDEMPOTENCY_KEY_INVALID`, `IDEMPOTENCY_KEY_REUSED`, and `OPERATION_NOT_FOUND`; verified `docs/adr/0013-operation-idempotency-key-and-lookup.md` and `docs/schemas/operation-lookup.schema.json` (including `code`).
3. Audited `app/src/test/kotlin/com/inscopelabs/buildengine/ArchitectureTest.kt` before writing code: `.mint(` remains confined exclusively to `CapabilityMinter.kt` and `ConfirmAndMintCapabilityUseCase.kt`.

---

## 2. Files Created and Modified

### Created Files
1. `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/dto/OperationLookupDto.kt`
2. `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/IdempotencyKeyGenerator.kt`
3. `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/OperationJournalEntity.kt`
4. `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/OperationJournalDao.kt`
5. `app/src/main/kotlin/com/inscopelabs/buildengine/data/local/OperationJournalDatabase.kt`
6. `app/src/main/kotlin/com/inscopelabs/buildengine/data/worker/OperationRecoveryWorker.kt`
7. `app/schemas/com.inscopelabs.buildengine.data.local.OperationJournalDatabase/1.json` (Room KSP schema export)
8. `app/src/test/kotlin/com/inscopelabs/buildengine/data/local/IdempotencyKeyGeneratorTest.kt`
9. `app/src/test/kotlin/com/inscopelabs/buildengine/core/network/OperationLookupWireTest.kt`
10. `app/src/test/kotlin/com/inscopelabs/buildengine/data/repository/OperationJournalExecutorTest.kt`
11. `agent-reports/2026-11-13T07-54-31Z-stage-3-2-journal-executor.md`

### Modified Files
1. `app/src/main/kotlin/com/inscopelabs/buildengine/domain/model/OperationOutcome.kt` — Completed IN PLACE (removed `SCAFFOLD PLACEHOLDER` and `TODO(...)`, defined the 6 sealed subtypes).
2. `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/dto/PublishRequestDto.kt` — Added `val idempotencyKey: String = ""`.
3. `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/dto/UploadRequestDto.kt` — Added `val idempotencyKey: String = ""`.
4. `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/dto/ReconcileRequestDto.kt` — Added `val idempotencyKey: String = ""`.
5. `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/dto/EngineScopeRequestDto.kt` — Added `val idempotencyKey: String = ""`.
6. `app/src/main/kotlin/com/inscopelabs/buildengine/core/network/GatewayEndpoints.kt` — Added `OPERATION_LOOKUP` constant and `operationLookup(operationId)` helper.
7. `app/src/main/kotlin/com/inscopelabs/buildengine/data/remote/GatewayApi.kt` — Added `suspend fun lookupOperation(idempotencyKey: String): OperationLookupDto`.
8. `app/src/main/kotlin/com/inscopelabs/buildengine/data/remote/GatewayRepository.kt` — Implemented `lookupOperation(idempotencyKey)`.
9. `app/src/main/kotlin/com/inscopelabs/buildengine/data/repository/OperationRepositoryImpl.kt` — Implemented journal write-before-send, AES-GCM cipher integration, idempotent key reuse, write outcome handling, `GET /operations/{id}` lookup mapping (§4.3 + live-head check), and recovery batching; left the 6 unimplemented `OperationRepository` facade methods (`publish`, `upload`, `reconcile`, `halt`, `resume`, `decide`) with their `TODO("SCAFFOLD: ...")` fail-closed markers for Stages 3.3–3.5.
10. `app/src/main/kotlin/com/inscopelabs/buildengine/di/DataModule.kt` — Minimal Hilt `@Provides` wiring for `OperationJournalDatabase`, `OperationJournalDao`, `JournalFieldCipher`, `IdempotencyKeyGenerator`, and `OperationRepositoryImpl` (required for `OperationRecoveryWorker`'s Hilt `EntryPointAccessors`).
11. `app/src/main/kotlin/com/inscopelabs/buildengine/BuildEngineApplication.kt` — Scheduled `OperationRecoveryWorker.enqueueLaunchRecovery(this)` on app launch per T3-203.
12. `app/src/test/kotlin/com/inscopelabs/buildengine/testutil/FakeGatewayApi.kt` — Added `lookupOperation` fake implementation to satisfy updated `GatewayApi` interface.

*Note on `OperationRepository.kt`:* Kept unchanged (no 7th abstract method added) because `PublishTaskUseCaseTest` asserts the `OperationRepository` interface exposes exactly the 6 privileged write operations and `OperationRecoveryWorker` in `data` consumes `OperationRepositoryImpl` directly via Hilt `@EntryPoint`.

---

## 3. Task Status

| Task | Description | Status | Notes |
|---|---|---|---|
| **T3-201** | `OperationOutcome` sealed hierarchy (`Succeeded`, `Refused`, `Conflict`, `Timeout`, `UncertainCompletion`, `RecoveryRequired`) | **Complete** | `Succeeded` enforces non-blank `auditRef` and `headSha`; `Timeout` is strictly reserved for write sends with no response. |
| **T3-202** | Encrypted Room journal, AndroidKeyStore AES-GCM field cipher, write-before-send, distinct lifecycle states | **Complete (JVM-verified with fake cipher/DAO; AndroidKeyStore & SQLite runtime unverified in-agent)** | `OperationJournalDatabase` exports schema `1.json`. `AndroidKeystoreJournalCipher` uses `AndroidKeyStore` AES-256-GCM with fresh 12-byte IV per field and fails closed via `JournalCryptoException`. |
| **T3-203** | Restart recovery via `OperationRecoveryWorker` (Hilt `@EntryPoint`, lookups only, exact §4.3 mapping & live-head check) | **Complete (mapping/recovery verified in JVM; WorkManager scheduling runtime unverified in-agent)** | Enqueued on launch in `BuildEngineApplication`; lookups only (`GET /operations/{id}` + `GET /branches/{name}/head`), never resends writes. |
| **T3-204** | Idempotency (same key reused for repeated taps on same in-flight intent; different intents get different keys) | **Complete** | Per-intent mutex + active-state fingerprint query suppresses duplicate writes while `INTENT_RECORDED`/`SENT`/`ACKNOWLEDGED`/`UNCERTAIN`. |
| **T3-205** | Unit tests covering crash scenarios, §4.3 matrix, replay, drift, key format, zero secret persistence/logging, and DTO schemas | **Complete** | All 379 debug unit tests pass (`gradle :app:testDebugUnitTest`). |

---

## 4. Proposed Names, Signatures, and Assumptions

- **New Types & Signatures:**
  - `com.inscopelabs.buildengine.core.network.dto.OperationLookupDto(id, operation, status, branch, headSha, auditRef, timestamp, code)`
  - `com.inscopelabs.buildengine.data.local.IdempotencyKeyGenerator(randomSource: SecureRandomSource)` with `fun generate(): String` and `companion fun isValid(key: String?): Boolean`
  - `com.inscopelabs.buildengine.data.local.OperationJournalState` enum (`INTENT_RECORDED`, `SENT`, `ACKNOWLEDGED`, `TERMINAL_SUCCEEDED`, `TERMINAL_REFUSED`, `TERMINAL_CONFLICT`, `UNCERTAIN`, `RECOVERY_REQUIRED`)
  - `com.inscopelabs.buildengine.data.local.OperationIntent`, `OperationIntentExtra`, `DecryptedJournalRecord`
  - `com.inscopelabs.buildengine.data.local.JournalCryptoException`, `JournalFieldCipher` (`encrypt`, `decrypt`), `AndroidKeystoreJournalCipher`
  - `com.inscopelabs.buildengine.data.local.OperationJournalEntity`, `OperationJournalConverters`
  - `com.inscopelabs.buildengine.data.local.OperationJournalDao` (`insert`, `update`, `findByKey`, `findByIntentFingerprintAndStates`, `findByStates`, `getAll`)
  - `com.inscopelabs.buildengine.data.local.OperationJournalDatabase` (`operationJournalDao()`)
  - `com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl.executeOperation(intent: OperationIntent, send: suspend (String) -> GatewayAckDto): OperationOutcome`
  - `com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl.lookupOperation(idempotencyKey: String): OperationOutcome`
  - `com.inscopelabs.buildengine.data.repository.OperationRepositoryImpl.recoverUncertainOperations(): RecoveryBatchSummary`
  - `com.inscopelabs.buildengine.data.worker.OperationRecoveryWorker` with `@EntryPoint @InstallIn(SingletonComponent::class) interface OperationRecoveryEntryPoint` and `companion fun enqueueLaunchRecovery(context: Context)`
- **Assumptions:**
  - Placing `val idempotencyKey: String = ""` at the end of write request DTOs maintains binary/source compatibility for existing callers while serializing the exact `"idempotencyKey"` JSON field whenever populated with a 32-hex key (and validated non-empty before sending in Stage 3.2/3.3+).
  - No `decide` or plan-status-transition request DTOs exist yet in `core/network/dto/` (only `PublishRequestDto`, `UploadRequestDto`, `ReconcileRequestDto`, `EngineScopeRequestDto` exist today).
  - Pre-send crashes leave rows in `INTENT_RECORDED` (recoverable via tap retry reusing the same key, 0 writes sent), while post-send interrupted/uncertain rows (`SENT`, `ACKNOWLEDGED`, `UNCERTAIN`) are resolved automatically on launch by `OperationRecoveryWorker` via `GET /operations/{id}` only.
  - Because `DataModule` and `BuildEngineApplication` are needed to satisfy Hilt bindings (`@EntryPoint` for `OperationRecoveryWorker`) and launch scheduling (T3-203), minimal wiring edits were applied there rather than leaving `OperationRecoveryWorker` unbound at compile time.

---

## 5. Stopped On / Blockers

- None encountered in Stage 3.2 scope.

---

## 6. Test Coverage Summary

Executed `gradle :app:testDebugUnitTest` — **379 tests completed, 0 failed** (including `ArchitectureTest`, all existing tests, and 30 new Stage 3.2 tests across 3 test suites):

- **`IdempotencyKeyGeneratorTest`:**
  - `generated keys match contract 32-hex format and are unique`: proves 32 generated keys match `^[0-9a-f]{32}$` with zero collisions.
  - `idempotency key is independent of and never equal to capability nonce`: proves independence from `DeviceNonceSource`.
  - `key validation rejects non-hex, uppercase, and wrong lengths`: proves strict format validation.
  - `OperationIntent rejects forbidden capability or secret substrings`: proves domain/data intent model rejects forbidden patterns (`ctxop-v1`).
- **`OperationLookupWireTest` (MockWebServer + real `GatewayRepository`):**
  - `write request DTOs serialize idempotencyKey per schemas`: verifies `"idempotencyKey"` JSON serialization across `PublishRequestDto`, `UploadRequestDto`, `ReconcileRequestDto`, `EngineScopeRequestDto`, and HTTP `POST` body over `MockWebServer`.
  - `lookupOperation decodes completed response and path-encodes idempotencyKey`: verifies `GET /operations/{id}` wire decoding (including unknown-field tolerance).
  - `lookupOperation decodes refused and pending responses`: verifies optional fields (`code`, `auditRef`, `headSha`) per schema.
  - `lookupOperation 404 throws GatewayHttpException with OPERATION_NOT_FOUND`: verifies 404 propagation to repository mapper.
- **`OperationJournalExecutorTest` (Fake DAO + RecordingFakeCipher + FakeGatewayApi + `Logger.logSink`):**
  - `Succeeded cannot be constructed without non-blank auditRef and headSha` & `G-08 accepted ack without auditRef or headSha never reports Succeeded`: proves sent/incomplete ack never reports `Succeeded`.
  - `crash between journal write and send sends nothing and leaves recoverable entry`: proves zero transport calls on pre-send crash, entry stays `INTENT_RECORDED`, and retry reuses the same key and sends once.
  - `crash between send and ack marks SENT and restart recovery resolves via lookup with zero resend`: proves process-death restart recovery resolves `SENT` via `GET /operations/{id}` without resending.
  - `network loss or timeout on write send returns Timeout then lookup resolves without resend`: proves `Timeout` reservation and lookup-only resolution.
  - `network failure or timeout on lookup leaves journal entry unchanged and signals retry`: proves lookup timeout/IO preserves journal state and sets `retryCount = 1`.
  - `replay refusal NONCE_REPLAYED maps to Conflict` & `head drift HEAD_DRIFT and HEAD_MOVED on write map to Conflict`: proves distinct conflict outcomes on write refusal.
  - `lookup completed with matching live head maps to Succeeded` & `lookup completed with moved live head maps to UncertainCompletion`: proves T3-302 live-head check on `completed` lookup.
  - `lookup pending maps to UncertainCompletion`: proves §4.3 `pending` row.
  - `lookup refused with every section 4_3 conflict code maps to Conflict`: tests all 7 conflict codes (`HEAD_DRIFT`, `HEAD_MOVED`, `NONCE_REPLAYED`, `SUBMISSION_IMMUTABLE`, `ANCESTRY_FAIL`, `RECONCILE_REQUIRED`, `IDEMPOTENCY_KEY_REUSED`).
  - `lookup refused with non-conflict codes including HALT_ACTIVE maps to Refused`: proves non-conflict refusal mapping.
  - `lookup failed maps to RecoveryRequired`: proves §4.3 `failed` row.
  - `lookup 404 OPERATION_NOT_FOUND maps to RecoveryRequired and never resends`: proves 404 fail-closed behavior and 0 resends.
  - `lookup with unknown status or missing or unknown code fails closed to RecoveryRequired`: proves fail-closed guarantees on unexpected payloads.
  - `repeated taps while operation is in flight reuse one key and never send a second write` & `concurrent double tap for one intent reuses one key and executes single write`: proves T3-204 sequential and concurrent deduplication plus distinct keys for distinct intents.
  - `journal insert failure before send aborts and sends nothing`, `encryption failure before send aborts and sends nothing`, & `decryption failure during recovery fails closed to RecoveryRequired and never returns default`: proves fail-closed persistence and crypto invariants.
  - `no capability, signature, or nonce ever enters journal entity, cipher inputs, or logs`: inspects `OperationJournalEntity.toString()`, all plaintexts passed to `cipher.encrypt`, and all captured `Logger.logSink` lines.

Also verified compilation via `compile_applet` (succeeded) and `gradle :app:compileDebugAndroidTestKotlin` (succeeded).

---

## 7. Unverified In-Agent & Deferred Device-Checklist Items

- **Unverified in-agent:**
  - Real `AndroidKeyStore` hardware/TEE AES-256-GCM key generation, encryption, and decryption (`AndroidKeystoreJournalCipher`).
  - Real Android SQLite Room persistence across OS process kills (`OperationJournalDatabase`).
  - Real `WorkManager` scheduling and execution with Hilt `EntryPointAccessors` (`OperationRecoveryWorker` triggered via `BuildEngineApplication`).
  - Live integration against Gateway A (`idempotencyKey` and `GET /operations/{id}`).
- **Deferred Device-Checklist Items:**
  1. Instrument / verify `AndroidKeystoreJournalCipher` round-trip encryption/decryption, unique per-field IVs, and fail-closed `KeyPermanentlyInvalidatedException` handling on an Android API 28+ device/emulator.
  2. Verify Room `OperationJournalDatabase` migration/persistence across real process termination and cold launch.
  3. Verify `OperationRecoveryWorker` execution under WorkManager `NetworkType.CONNECTED` constraint on device/emulator.
  4. Re-verify end-to-end against Gateway A once Gateway A implements `idempotencyKey` write-ahead journaling and `GET /operations/{id}`.

---

## 8. Mandatory Confirmations

- **Gateway A status:** Confirmed — Gateway A does **not** yet implement `idempotencyKey` or `GET /operations/{id}`; all gateway behavior in Stage 3.2 was implemented and tested against fakes (`FakeGatewayApi` and `MockWebServer`).
- **Zero-secret invariant:** Confirmed — No capability string, signature, or capability nonce is stored in `OperationJournalEntity`, encrypted in cipher payloads, used as an idempotency key, or emitted in logs.
- **Protected paths:** Confirmed — Zero files under `gateway/**`, `docs/**`, `.github/workflows/*.yml`, `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `version.properties`, `build-logs/**`, or `network_security_config.xml` were created or modified, and `ArchitectureTest.kt` and the capability codec were untouched.
