# Operator Console Android — Independent Phase 3 Remediation Register

**Document ID:** `OC-PR3-RR-001`  
**Repository:** `inscope-labs/operator-console`  
**Revision:** `1.1` — verified against the repository and corrected before installation (see Appendix A)  
**Install path:** `docs/release/PHASE-3-REMEDIATION-REGISTER.md`, via a Claude branch and pull request that the operator merges (ADR 0010)  
**Assessment baseline:** `732b7fbc41e3a139ead15db0d09f1c0e498ff347`  
**Verification head:** `7fdd304` (`main` on 2026-11-13). Findings below were re-checked at this commit  
**Controlling plan:** `RELEASE-PLAN.md`, Phase 3, Stages 3.1–3.6  
**Register status:** Corrected draft — takes effect only when the operator merges the installing PR; it authorizes no implementation by itself (see §8)  
**Purpose:** Establish an independently auditable set of completion requirements that must be satisfied before Stage 4 begins.

---

## 1. Purpose and governing rule

This register converts the Phase 3 findings into discrete implementation, verification and acceptance requirements. It is intended to be installed as a separate repository document only after operator review.

**Stage 4 must not begin until every mandatory item in this register is closed and the Phase 3 exit gate is formally accepted.** “Code exists”, “agent report says complete”, or “JVM tests pass” is not, by itself, closure evidence.

The register is independent in the sense that it supplies its own task IDs, acceptance criteria, evidence requirements, scoring method and gate. It does not replace or silently rewrite the controlling release plan. Existing task IDs are retained as traceability references.

### 1.1 Scope

Included:

- Android Kotlin application code, domain/use-case layer, repository, UI elements explicitly assigned to Phase 3, local persistence, cryptographic use, network client integration, WorkManager, tests, CI evidence and Phase 3 documentation.
- The Android application's correct consumption of the published Gateway A contract.
- Controlled-gateway integration tests as a verification environment, without auditing or modifying gateway implementation.

Excluded:

- Gateway A source code, Cloudflare deployment, gateway storage/authorization implementation, GitHub App credentials and gateway release readiness.
- Phase 4 interface completion beyond the minimum Phase 3 operation confirmation/progress/result UI explicitly required by Stage 3.3.
- Phase 5 Lab, Phase 6 Class B workflows, Phase 7 release machinery and Phase 8 publication/retirement.
- Unimplemented later-phase code. Its presence must not be counted as Phase 3 completion.

### 1.2 Interpretation of “90+ readiness”

Completion of this register is designed to produce a **minimum 90/100 Phase 3 readiness score**, provided all mandatory gates pass and the evidence is independently reviewable.

This is a Phase 3 score, not a promise that the entire Android application is ready for production distribution. Phase 4 and later remain outside this register; Phase 2 deployment-specific acceptance and later release gates remain separate requirements for an eventual production release.

A score cannot be earned by marking tasks complete without evidence. Any hard-gate failure caps the Phase 3 score below 90 regardless of the arithmetic score.

---

### 1.3 Requirement provenance

Every task is either required by `RELEASE-PLAN.md` or added by this register. The distinction matters because the plan's Gate 3 text is the controlling definition of Phase 3.

- **[PLAN]** — required by the plan's Gate 3 or by a Phase 3 stage task. Closing it is part of Gate 3.
- **[REG+]** — added by this register on top of the plan. These are stricter than the plan's Gate 3 text. They become Gate 3 requirements only if the operator approves them when merging this register; otherwise they move to the stage the plan already assigns them to.

| Item | Provenance | Where the plan places it today |
|---|---|---|
| PR3-040, PR3-041 (on-device Room, Keystore, WorkManager runtime evidence) | [REG+] | Stage 7.2 device acceptance `[OP]` (T7-201); `docs/STATUS.md` already defers on-device runtime to it |
| PR3-043 (required CI jobs including instrumented tests) | [REG+] | Stage 7.1 `[CLAUDE]` (T7-101, protected `.github/workflows/*.yml`) |
| AES-GCM associated-data requirement in PR3-010 | [REG+] | Not in the plan (BD-3.2 requires Keystore AES-GCM only) |
| Weighted score, hard-gate caps and the 90/100 threshold (§6) | [REG+] | The plan has no scoring model |
| All other PR3 tasks | [PLAN] | Stages 3.1 to 3.6 and Gate 3 |

Merging this register with these items accepted amends Gate 3 for Phase 3, and `RELEASE-PLAN.md` must then be revised by a separate operator-approved change (§8).

---

## 2. Baseline findings this register addresses

### 2.1 Verified status at `7fdd304`

The assessment baseline `732b7fb` is the merge of the Gateway A idempotency PR (#61). `main` has since moved to `7fdd304`. Each finding was re-checked there by static inspection of the public repository. No Gradle build, test run or device was used for this verification.

| # | Baseline finding | Status at `7fdd304` | Evidence |
|---|---|---|---|
| 1 | Publish repository method and use case are scaffolds | **Confirmed** | `PublishTaskUseCase.kt` carries `SCAFFOLD PLACEHOLDER` and `TODO("SCAFFOLD: …")`; `OperationRepositoryImpl.kt` `publish` is a `TODO` |
| 2 | Upload, reconcile, halt, resume and decide paths are incomplete | **Confirmed** | `OperationRepositoryImpl.kt` lines ~774-789 are `TODO("SCAFFOLD: …")` for publish, upload, reconcile, halt, resume and decide; the matching use cases carry markers |
| 3 | Task-status and work-order use cases are scaffolds | **Confirmed** | `SetTaskStatusUseCase.kt`, `CreateWorkOrderUseCase.kt` carry markers |
| 4 | The `decide` Android API and DTO are not established | **Confirmed, with a correction** | `GatewayApi.kt` has publish, reconcile, upload, halt, resume, sync, doctor, enroll and lookup methods only: **no decide and no status-transition method or DTO**. The contract **does** define both (see PR3-003) |
| 5 | Keystore, Room and WorkManager behaviour is demonstrated only by JVM tests | **Confirmed** | No `androidTest` file references Room, WorkManager or the journal cipher; `docs/STATUS.md` itself defers on-device runtime |
| 6 | Synchronous acknowledgement does not apply the live-head check that recovery applies | **Not independently confirmed** | `OperationRepositoryImpl.kt` ~L449-528 applies a live-head check on the lookup path; the synchronous acknowledgement path must be re-inspected under PR3-013 |
| 7 | Per-operation minimum authentication tier is not enforced | **Plausible, not fully confirmed** | `AuthTierResolver.kt` exists; a static search found no per-operation minimum-tier policy symbol. Re-inspect under PR3-030 |
| 8 | `ArchitectureTest` does not enforce the plan's restrictions | **Confirmed** | The file has one test, `.mint(` confinement. T3-601 also requires token-pattern, `Runtime.exec`/`ProcessBuilder`, journal-bypass and `lab` import checks |
| 9 | Status records do not reflect the newer implementation | **Partly stale** | `docs/STATUS.md` was updated on 2026-11-13 and now lists Stages 3.1 and 3.2 as implemented. It **overstates** Stages 3.5 and 3.6 as implemented (see PR3-001) |
| 10 | CI does not prove the Android runtime or operation failure matrix | **Confirmed** | `android-ci.yml` has no emulator or instrumented-test job |

### 2.2 Additional finding

- `app/proguard-rules.pro` is a protected path (`[CLAUDE]` via branch and pull request, plan rule G-01 and the protected-paths row). Commit `7fdd304`, titled "feat: implement Room persistence and WorkManager", modified it, and `docs/STATUS.md` records Stage 3.1 as signed off on the strength of that commit. See PR3-002.

### 2.3 What exists and is credited as progress

The baseline showed meaningful progress in the Stage 3.2 foundation: an encrypted Room journal with Keystore AES-GCM field encryption, the sealed outcome model, idempotency keys, operation lookup and a recovery worker. These are inputs to the tasks below, not closed tasks.

---

## 3. Non-negotiable invariants

All implementation and testing under this register must preserve these invariants.

**I-01 — Human authority:** A privileged action requires an explicit operator confirmation. UI state, a loaded screen, device authentication alone, or possession of a credential must not be interpreted as authorization.

**I-02 — One minting path:** Capability minting remains confined to the existing authorized `ConfirmAndMintCapabilityUseCase` path. No use case, ViewModel, repository or UI component may mint independently.

**I-03 — Journal before send:** A durable operation intent and idempotency identity must be committed before the first network dispatch.

**I-04 — No secret persistence:** The journal must never persist a signed capability, signature, private key, nonce or raw operator credential. Persist only the minimum metadata needed to identify and recover an operation.

**I-05 — No blind retry:** The Android application must never automatically resend a write whose dispatch may have reached the gateway. Unknown outcomes are resolved by operation lookup and, if still unresolved, surfaced as uncertain/recovery-required.

**I-06 — No false success:** A success state is shown only after the required gateway acknowledgement, operation lookup/reconciliation where necessary, and live branch-head validation have all succeeded.

**I-07 — Scope integrity:** Each operation uses the exact operation, environment, plan/task/order, branch and head scope required by the published contract. Repository-wide halt/resume must not reuse a task-scoped capability.

**I-08 — Fail closed:** Missing configuration, stale head, authentication failure, pinning failure, key invalidation, malformed response, unknown operation state or contract mismatch must not fall through to success.

**I-09 — No alternative execution channel:** The Android application must not use Git credentials, shell execution, `Runtime.exec`, `ProcessBuilder`, local Git, or a second write path around the journal.

**I-10 — Contract fidelity:** The application implements only operations and request/response shapes defined by the authoritative, versioned gateway contract. A missing or contradictory contract is a release blocker, not permission to invent a client endpoint.

**I-11 — Environment isolation:** Staging and production credentials/keys/configuration remain isolated. A capability or enrolled key from one environment must not be silently reused in another.

**I-12 — Auditability:** Each terminal result retains the operation identifier and audit reference where supplied, with a clear distinction between success, refusal, conflict, timeout, uncertain completion and recovery required.

---

## 4. Remediation tasks

Task status is initially **OPEN** for every item. Implementation agents must not self-assign **ACCEPTED**. The operator or designated reviewer closes tasks after inspecting evidence.

### Workstream A — Baseline, dependencies and contract readiness

#### PR3-001 — Reconcile Phase 3 implementation baseline

- **Traceability:** T3-101; Gate 3; status documentation.
- **Priority:** Critical.
- **Work:** Re-inspect the current `main` commit. Inventory all Phase 3 classes, TODO/SCAFFOLD markers, tests, dependencies, reports and CI jobs. Correct stale Phase 3 status claims without changing the scope of Phases 4–8.
- **Acceptance:**
  - Every Phase 3 task is mapped to actual source files and tests.
  - The status document distinguishes implemented, JVM-tested, Android-runtime-tested, integration-tested and operator-accepted.
  - Gateway-only reports are not counted as Android evidence. The existing `docs/release/PHASE-*-COMPLETION-REPORT.md` files are Gateway A reports and are not Phase 3 Android evidence.
  - The `docs/STATUS.md` rows for Stages 3.5 and 3.6 are corrected to match what the code and tests actually prove.
  - Status wording uses the plan's vocabulary (implemented, reference only, scaffold, not started, not deployed, unsupported).
  - No Phase 3 item is marked complete solely because a file exists.
- **Evidence:** Baseline inventory, changed status document, source/test path map and reviewer checklist.

#### PR3-002 — Verify dependency and build configuration

- **Traceability:** T3-101; BD-3.2; BD-3.3.
- **Priority:** Critical.
- **Work:** Verify Room, WorkManager, KSP, serialization, Keystore/cipher and test dependencies are declared and wired correctly. Verify the approved dependency/build-file change is present in the repository history. Remove the deferred Play Integrity dependency from the applicable Android build configuration as required by the controlling plan.
- **Acceptance:**
  - Clean Gradle sync and build succeed from a clean checkout.
  - Room schema export and migration configuration are deterministic.
  - WorkManager is correctly initialized and available to the application.
  - No unnecessary manifest component/permission is introduced.
  - ProGuard/R8 rules preserve required Room, serialization and worker behaviour.
  - Dependency versions are pinned through the repository's existing version-management approach.
  - **Provenance of protected-file edits:** every change to `app/proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts` and `gradle/libs.versions.toml` is reviewed by Claude. Commit `7fdd304` changed `proguard-rules.pro` outside the `[CLAUDE]` path; the operator either accepts it in writing after Claude's review or has it re-landed through a `[CLAUDE]` pull request.
- **Evidence:** Dependency diff, Gradle task output, schema artifacts and build logs.

#### PR3-003 — Freeze and verify the Android-consumed gateway contract

- **Traceability:** T3-301 through T3-404; I-10.
- **Priority:** Critical. **Provenance:** [PLAN].
- **Verified starting point:** Contract revision 1.1 (`docs/gateway-contract.md`, merged in PR #60) defines `POST /plans/{id}/decide` (§3.18, `decide.schema.json`) and `POST /plans/{id}/status` (§3.17, `plan-status-transition.schema.json`), and both require `idempotencyKey`. The Android `GatewayApi` has **no** method or DTO for either. The contract lists `POST /plans/{id}/order` in its §2 table but has no §3 section or schema for it.
- **Work:** Compare every Android request DTO, response DTO, endpoint, error mapping and operation enum against contract revision 1.1 and the schemas. Add the missing client methods and DTOs for `decide` and status transition exactly as the schemas define them. Resolve the following contract questions, each through an approved contract revision or an explicit operator-approved reduction, never by inventing fields:
  1. T3-404 and plan item 7 require decisions to carry attribution, rationale, scope and evidence. The `decide` request schema has only `plan`, `task`, `branch`, `decision`, `notes` (plus `idempotencyKey` and `capability`). Say how each required field is represented.
  2. The `decide` and status-transition responses carry no `branch` or `headSha`. State what "live check" means for them.
  3. State how the lookup (`GET /operations/{id}`) records and reports these operations.
- **Acceptance:**
  - Every implemented Android operation has an authoritative endpoint, method, request schema, response schema and error mapping.
  - `decide` and status transition are implemented against the published schemas, or the unresolved questions above are recorded as operator-approved decisions. An operation that is not implemented does not pass Gate 3.
  - Operations the contract does not define (work-order creation, see PR3-027) are not implemented and not exposed as functional.
  - Unknown fields and error codes, and malformed response bodies, fail closed.
  - Contract vectors used by Android tests match the frozen contract revision.
- **Evidence:** Contract-to-client traceability matrix, schema/vector test results and the contract revision identifier.
- **Dependency note:** Answering the questions above may require a separately approved contract change. It does not authorize gateway source implementation under this Android register.

---

### Workstream B — Durable operation journal and recovery

#### PR3-010 — Complete journal schema and encrypted-field policy

- **Traceability:** T3-201, T3-202; BD-3.2.
- **Priority:** Critical.
- **Work:** Review and finalize `OperationOutcome`, journal entity/DAO/database and field cipher.
- **Acceptance:**
  - The repository boundary exposes explicit outcomes equivalent to `Succeeded`, `Refused`, `Conflict`, `Timeout`, `UncertainCompletion` and `RecoveryRequired`.
  - Journal records contain operation identity/type, exact non-secret scope metadata, idempotency key, lifecycle state, timestamps, gateway operation ID and audit reference as applicable.
  - No capability, signature, nonce, private key or raw credential is persisted.
  - Sensitive journal fields use the approved Keystore-wrapped AES-GCM field-encryption design.
  - Encryption uses a unique IV per encryption. **[REG+]** Ciphertext is also bound to its record and column through GCM associated data (the current cipher prepends the IV but sets no associated data).
  - Decryption failure, key invalidation and corrupt records fail closed and are diagnosable without logging plaintext.
  - Database schema versioning and migration behaviour are explicit.
- **Evidence:** Schema review, cipher tests, database tests, migration tests and data-classification table.

#### PR3-011 — Enforce atomic intent-before-dispatch

- **Traceability:** T3-202; I-03.
- **Priority:** Critical.
- **Work:** Ensure the journal intent and idempotency key are durably committed before any write request can be sent.
- **Acceptance:**
  - A failed journal transaction results in zero network dispatches.
  - The executor cannot dispatch an operation without a committed journal record.
  - A crash after journal commit but before dispatch is recoverable without creating a second operation identity.
  - State transitions are validated and cannot move backwards or skip required states.
  - Concurrent taps/coroutines for the same logical intent cannot create duplicate dispatches.
- **Evidence:** DAO transaction tests, executor tests, concurrency tests and fault-injection logs.

#### PR3-012 — Complete idempotency and duplicate-intent protection

- **Traceability:** T3-204; I-05.
- **Priority:** Critical.
- **Work:** Verify idempotency-key generation, intent fingerprinting, duplicate-tap handling and gateway replay semantics as consumed by the client.
- **Acceptance:**
  - The same user intent reuses its existing key while in flight or unresolved.
  - A materially different operation/scope produces a different key.
  - Reusing a key with a different fingerprint is treated as a conflict and never dispatched as a new write.
  - Process restart does not generate a new key for an unresolved intent.
  - Idempotency keys are not confused with capability nonces.
- **Evidence:** Deterministic key tests, collision/fingerprint tests and duplicate-tap tests.

#### PR3-013 — Make acknowledgement and live-head verification consistent

- **Traceability:** T3-302; T3-205; I-06.
- **Priority:** Critical.
- **Work:** Unify synchronous acknowledgement handling and recovery lookup so both apply the same terminal-success criteria.
- **Acceptance:**
  - Nonblank `auditRef`/`headSha` alone is insufficient to report success.
  - The operation acknowledgement/lookup must match the journalled operation ID and expected operation/scope.
  - Required branch and full head SHA are checked against the live branch head.
  - Head drift, missing audit evidence, malformed lookup, contradictory state or unavailable verification results in `UncertainCompletion`, `Conflict` or `RecoveryRequired`, never success.
  - The result is monotonic: a terminal refusal/conflict cannot later be overwritten as success without an explicit, contract-defined reconciliation rule.
- **Note:** The recovery lookup path already performs a live-head check (`OperationRepositoryImpl.kt`, ~L449-528). Re-inspect the synchronous acknowledgement path and make the two share one code path.
- **Evidence:** Unit tests for both acknowledgement and recovery paths, including head drift and contradictory response fixtures.

#### PR3-014 — Complete restart recovery and WorkManager policy

- **Traceability:** T3-203, T3-205.
- **Priority:** Critical.
- **Work:** Verify startup recovery discovery, WorkManager scheduling, unique work policy, constraints, cancellation and retry behaviour.
- **Acceptance:**
  - Recovery performs operation lookups only; it never resends the original write.
  - Unique work prevents duplicate recovery workers for the same operation.
  - Transient lookup failures use bounded backoff and do not mutate the operation into success.
  - Permanent/authentication/policy failures remain distinguishable.
  - An unresolved operation remains visible as unresolved and can be manually revisited.
  - Recovery works after process death, force-stop/relaunch and device reboot to the extent supported by Android/WorkManager scheduling semantics.
  - Worker input contains identifiers only, never secrets or capabilities.
- **Evidence:** WorkManager instrumentation tests, restart/reboot test record and worker-state traces.

#### PR3-015 — Prove crash and network fault matrix

- **Traceability:** T3-205; Gate 3.
- **Priority:** Critical.
- **Work:** Implement deterministic fault-injection coverage for all critical boundaries.
- **Mandatory scenarios:**
  1. Crash before journal commit.
  2. Journal commit failure.
  3. Crash after journal commit and before send.
  4. Network loss before request reaches gateway.
  5. Gateway accepts write but response is lost.
  6. Crash after send and before acknowledgement persistence.
  7. Malformed or truncated acknowledgement.
  8. Lookup reports pending.
  9. Lookup reports terminal success.
  10. Lookup reports refusal/conflict.
  11. Lookup is unavailable repeatedly.
  12. Live branch head has moved.
  13. Journal ciphertext cannot be decrypted.
  14. Duplicate taps during an in-flight operation.
- **Acceptance:** No scenario creates a second write automatically or falsely reports success. Every scenario ends in a valid, explainable journal state.
- **Evidence:** Scenario matrix with test identifiers, expected/actual states and captured test output.

### Workstream C — Complete privileged operation paths

#### PR3-020 — Implement the shared journal-backed write executor

- **Traceability:** T3-301 through T3-404; I-02, I-03, I-09.
- **Priority:** Critical.
- **Work:** Establish one shared execution path used by every privileged operation. The path must bind the operator confirmation result, minted capability, journal intent, idempotency identity, network dispatch, acknowledgement/lookup and final result.
- **Acceptance:**
  - No write use case calls the network client directly.
  - No repository write method bypasses the journal.
  - Capability material exists only as long as required in memory for dispatch and is never logged or journalled.
  - Operation type and scope are revalidated immediately before dispatch.
  - Every result maps to the sealed outcome model.
  - Cancellation after dispatch is treated as potentially uncertain, not as proof that the remote operation was cancelled.
- **Evidence:** Architecture tests, call-graph review and shared-executor tests.

#### PR3-021 — Complete publish

- **Traceability:** T3-301, T3-302.
- **Priority:** Critical.
- **Work:** Implement `PublishTaskUseCase`, `PublishViewModel`, confirmation/progress/result UI and the repository publish path.
- **Acceptance:**
  - The operator sees operation, plan, task/order, branch, full 40-character head SHA, capability TTL/countdown, posture/tier and relevant guard summary before confirmation.
  - Confirmation is explicit and bound to the displayed operation/scope/head.
  - A changed head or expired capability invalidates the confirmation and requires a fresh review.
  - Publish uses the shared executor and durable journal.
  - Success requires gateway confirmation plus live-head validation.
  - `auditRef` and operation ID are shown on terminal success.
  - Refusal, conflict, timeout, uncertain completion and recovery-required are separately represented.
  - Real tests cover each distinct refusal (scope refused, replayed, head drifted, halt active), a declined confirmation, and TTL expiry while the confirmation prompt is open (T3-303).
  - `OperationResultBanner` shows `auditRef` and carries no scaffold marker.
- **Evidence:** Use-case/ViewModel tests, Compose/UI state tests, controlled-gateway integration test and screenshots or test artifacts.

#### PR3-022 — Complete upload

- **Traceability:** T3-403.
- **Priority:** Critical.
- **Work:** Implement upload request preparation, archive validation, digest verification, confirmation and journal-backed dispatch.
- **Acceptance:**
  - Archive bytes and SHA-256 are derived from the same immutable byte sequence.
  - The archive is chosen through the system document picker, with no storage permission added.
  - Archive size/type/path restrictions follow the authoritative contract; the size cap and client pre-checks mirror `cbridge`.
  - The original archive is preserved.
  - No archive payload or capability is written to logs or journal.
  - Upload is bound to the correct task, submission, branch and head.
  - Retries/recovery obey the no-blind-resend invariant.
  - The user receives a distinct terminal or uncertain result.
- **Evidence:** Digest/size/path tests, redaction tests, executor tests and controlled-gateway integration result.

#### PR3-023 — Complete reconcile

- **Traceability:** T3-402.
- **Priority:** Critical.
- **Work:** Implement reconcile request preparation and journal-backed execution.
- **Acceptance:**
  - Reconcile is bound to the intended task, submission, branch and exact head.
  - Stale or changed head is rejected or surfaced as conflict.
  - The operation cannot be mistaken for publish or upload.
  - Success requires the contract-defined terminal acknowledgement and live-head validation.
  - The `MERGED:` or `CLOSED:` outcome from the acknowledgement is shown; the app never merges anything.
  - Unknown outcome is resolved by lookup, not resubmission.
- **Evidence:** Scope-binding tests, head-drift tests and controlled-gateway integration result.

#### PR3-024 — Complete halt and resume

- **Traceability:** T3-401.
- **Priority:** Critical.
- **Work:** Implement halt/resume use cases, confirmation summaries, repository/API calls and journal-backed outcomes.
- **Acceptance:**
  - Halt/resume use the engine/repository-wide scope required by the contract, not a task-scoped capability.
  - Reason and affected scope are shown before confirmation where required.
  - Resume cannot be performed using stale or inappropriate scope, and requires the recorded halt cause.
  - The app displays authoritative resulting engine state, not merely the HTTP response.
  - Duplicate taps and uncertain responses follow the shared idempotency/recovery rules.
- **Evidence:** Scope tests, confirmation tests and controlled-gateway halt/resume integration results.

#### PR3-025 — Complete decide

- **Traceability:** T3-404; BD-1.3; BD-3.1; I-10.
- **Priority:** Critical. **Provenance:** [PLAN].
- **Work:** Implement the Android API method, DTOs, use case, confirmation path and journal-backed result handling for `POST /plans/{id}/decide`, which contract revision 1.1 defines (§3.18). Do the UI fields required by T3-404 (attribution, rationale, scope, evidence) only as far as PR3-003 has settled how they map onto the schema.
- **Acceptance:**
  - The request and response match `decide.schema.json`, including `idempotencyKey`.
  - Decision type, target, notes and scope are displayed accurately before confirmation.
  - The confirmation tier required by BD-3.1 is enforced (PR3-030).
  - The decision is journalled and recovered using the same executor.
  - Unsupported decisions are not fabricated or silently mapped to another operation.
- **Evidence:** Contract traceability, DTO/vector tests, use-case tests and controlled-gateway integration result.
- **Hard blocker:** If PR3-003 leaves the attribution, rationale and evidence fields without an approved mapping, this task remains OPEN until the operator approves a contract revision or a documented reduction.

#### PR3-026 — Complete task-status transition

- **Traceability:** Gate 3; T3-404; BD-1.3; BD-1.4; ADR 0008. The contract defines `POST /plans/{id}/status` (§3.17); the Android client has no method or DTO for it yet.
- **Priority:** Critical.
- **Work:** Implement status changes supported by the authoritative contract, including any required completion evidence and task-scoped capability.
- **Acceptance:**
  - Only contract-supported transitions are offered.
  - Invalid transitions are rejected before dispatch where locally knowable and always fail closed if refused remotely.
  - “Done” or equivalent completion status requires the contract-defined evidence.
  - Status changes use the shared journal-backed executor.
  - The displayed status is refreshed from authoritative state after the operation.
- **Evidence:** Transition-table tests, evidence-validation tests and controlled-gateway integration results.

#### PR3-027 — Work-order creation: documented alternative, not implementation

- **Traceability:** T3-404 (`CreateWorkOrderUseCase`); BD-1.3.
- **Priority:** High.
- **Verified starting point:** The plan says `CreateWorkOrderUseCase` is built only if BD-1.3 says so, otherwise a documented alternative screen. The plan's recommended BD-1.3 option is B: add `decide` and guarded status transitions only; work orders stay with engine tooling. The contract's §2 table lists `POST /plans/{id}/order` but defines no §3 request or response and no schema, so I-10 forbids implementing it on the client.
- **Work:** Confirm the operator's recorded BD-1.3 decision. Unless the operator chose otherwise, defer work-order creation: remove `CreateWorkOrderUseCase` from any operational path, show the documented alternative, and record the deferral in `docs/STATUS.md` and the evidence dossier.
- **Acceptance:** No scaffolded work-order action is represented as operational. If the operator instead chose to support it, an approved contract revision defines the request and response first, and the work then follows the same executor, confirmation, journal and recovery rules.
- **Evidence:** The operator's BD-1.3 decision reference and the status record.
- **Gate treatment:** A deferral under BD-1.3 option B is a plan-consistent disposition and does not block Gate 3.

---

### Workstream D — Confirmation, device security and architecture boundaries

#### PR3-030 — Enforce per-operation authentication tier

- **Traceability:** T3-501; BD-3.1.
- **Priority:** Critical.
- **Assumption:** BD-3.1 is encoded as the plan's recommended option (a): both tiers accepted in staging and production, the gateway records the tier, and `decide` and `resume` require `hardware_confirm` when the device can provide it. The operator's recorded BD-3.1 closure must be cited; if it differs, this task changes with it.
- **Work:** Encode the approved minimum authentication tier by environment and operation. Show the effective tier and reason in the confirmation summary.
- **Acceptance:**
  - The policy is explicit, centrally defined and unit-tested.
  - A device lacking biometric hardware can still use permitted device-credential/in-app paths where policy allows.
  - Operations requiring `hardware_confirm` are never silently downgraded.
  - `decide` and `resume` enforce the stronger requirement specified by BD-3.1 when hardware confirmation is available/required by policy.
  - Environment differences are enforced and tested.
  - Authentication success is bound to the exact pending operation and cannot be reused for a different scope.
- **Evidence:** Policy matrix, resolver tests, confirmation-use-case tests and device test record.

#### PR3-031 — Verify no-biometric operator path

- **Traceability:** T3-502.
- **Priority:** High.
- **Work:** Exercise the supported no-biometric device path end to end, including typed confirmation prefix where required.
- **Acceptance:**
  - Read-only application functionality remains available.
  - Permitted operations work with the allowed confirmation tier.
  - Hardware-only operations are blocked with an intelligible reason.
  - No fallback silently weakens the policy.
- **Evidence:** Real-device or representative emulator test record, including negative cases.

#### PR3-032 — Handle key invalidation and re-enrolment safely

- **Traceability:** T3-503.
- **Priority:** Critical.
- **Work:** Detect invalidated/removed Keystore keys and route the application to a `NeedsReenrollment` state. Ensure the old key identifier is not treated as valid after invalidation.
- **Acceptance:**
  - Biometric enrollment changes and lock-screen removal are handled according to the selected Keystore policy.
  - Signing failures caused by invalidation are distinguished from transient errors.
  - The old `kid` is marked unusable locally and revocation is requested through the published contract where supported.
  - Re-enrolment creates a new key identity and does not restore old authorization.
  - Pending operations remain unresolved/recoverable; key loss does not cause automatic write replay.
- **Evidence:** Keystore instrumentation tests, re-enrolment flow tests and revocation-contract traceability.

#### PR3-033 — Complete architecture and secret-hygiene tests

- **Traceability:** T3-601.
- **Priority:** Critical.
- **Work:** Extend `ArchitectureTest` and related CI checks to enforce every Phase 3 architectural restriction.
- **Acceptance:**
  - No GitHub token/PAT or repository credential patterns in application source/resources.
  - No `Runtime.exec`, `ProcessBuilder` or shell/Git execution path.
  - `.mint(` is used only in the authorized capability-minting path.
  - No write use case or UI component can call a gateway write endpoint outside the shared journal executor.
  - No production package imports Lab/HMAC-only code.
  - No capability, signature, nonce, private key or operator credential is logged or persisted.
  - Tests fail on deliberate violations (negative fixtures or controlled mutation tests).
- **Evidence:** Architecture test output, negative-test evidence and CI run URL/ID.

### Workstream E — Android runtime verification and Phase 3 gate evidence

#### PR3-040 — Add Android instrumentation coverage for persistence and crypto

- **Traceability:** T3-202, T3-205.
- **Priority:** Critical. **Provenance:** [REG+] (the plan places on-device runtime proof in Stage 7.2).
- **Work:** Test the actual Room database and actual Android Keystore cipher on an Android runtime, not only fake/JVM implementations.
- **Acceptance:**
  - Database create/read/update and transaction rollback pass.
  - Encrypted values survive process restart and can be decrypted with the expected key.
  - Ciphertext tampering and wrong-key/deleted-key cases fail closed.
  - Migration tests pass for every schema migration introduced by Phase 3.
  - No sensitive plaintext appears in database inspection or logs.
- **Evidence:** Instrumentation test report and emulator/device configuration.

#### PR3-041 — Add WorkManager instrumentation coverage

- **Traceability:** T3-203, T3-205.
- **Priority:** Critical. **Provenance:** [REG+].
- **Work:** Exercise actual worker enqueue, unique-work behaviour, constraints, retry/backoff, cancellation and restart recovery.
- **Acceptance:** The worker performs lookup-only recovery and cannot trigger a write resend. Duplicate workers do not create duplicate state transitions.
- **Evidence:** Instrumentation test report and worker execution trace.

#### PR3-042 — Add operation-level integration test suite

- **Traceability:** T3-301 through T3-404; Gate 3.
- **Priority:** Critical.
- **Work:** Run each supported Phase 3 operation against a controlled test gateway implementing the frozen contract.
- **Acceptance:**
  - Publish, upload, reconcile, halt, resume, decide and status transition each have a positive test where contract-supported.
  - Each operation has refusal, conflict, timeout/unknown-outcome and stale-head coverage where applicable.
  - The test proves the Android client sends the correct scope and idempotency key.
  - The test proves operation lookup/recovery behaviour after simulated lost acknowledgement.
  - Gateway source/deployment changes are not included in this Android remediation PR unless separately authorized.
- **Environment dependency:** A controlled gateway must exist: the deployed `test` Worker or a local harness. Gateway A's idempotency record and lookup merged in PR #61, but whether any gateway is deployed and implements status transition is not established by this verification. The operator names the environment before this task starts.
- **Evidence:** Per-operation result matrix, environment identifier, contract revision, test run and sanitized logs.

#### PR3-043 — Make Phase 3 verification mandatory in CI

- **Traceability:** Gate 3; T3-205, T3-601.
- **Priority:** Critical. **Provenance:** [PLAN] for JVM, architecture and contract checks; [REG+] for instrumented-test jobs.
- **Ownership:** `.github/workflows/*.yml` are protected. This task is a `[CLAUDE]` branch and pull request that the operator merges, and it pulls part of T7-101 forward.
- **Work:** Ensure required JVM, architecture, contract, instrumentation and applicable integration tests run as required checks for Phase 3 changes.
- **Acceptance:**
  - Required test jobs fail the workflow on failure; no `|| true` or silent continue-on-error for required checks.
  - Test reports are retained and linked from the Phase 3 evidence document.
  - CI uses deterministic dependency resolution and a clean checkout.
  - A green JVM-only run cannot be represented as full Phase 3 acceptance.
  - Instrumented tests run on an emulator job; a device-only alternative must be named and operator-approved.
- **Evidence:** Successful required CI run and workflow configuration review.

#### PR3-044 — Produce and accept Phase 3 evidence dossier

- **Traceability:** Gate 3; release-plan evidence convention.
- **Priority:** Critical.
- **Work:** Create `docs/release/PHASE-3-EVIDENCE.md` (or the exact evidence path required by the controlling plan) and link every task to implementation and test evidence.
- **Acceptance:**
  - All PR3 tasks are marked ACCEPTED, DEFERRED by an approved scope decision, or OPEN; no ambiguous status.
  - Every mandatory task has source references, test IDs/results and reviewer identity/date.
  - The dossier explicitly records unresolved risks and confirms none violates a hard gate.
  - Phase 3 status in `docs/STATUS.md` and `RELEASE-PLAN.md` is consistent.
  - The operator explicitly accepts Gate 3 before Stage 4 starts.
- **Note:** The path follows the plan's evidence convention (`docs/release/PHASE-<n>-EVIDENCE.md`, plan rule 8 and the task-ID section).
- **Evidence:** Committed evidence dossier and recorded operator approval.

---

## 5. Required test matrix

The following matrix is a minimum, not an exhaustive substitute for task-specific tests.

| Test family | Minimum required proof | Gate impact |
|---|---|---|
| Clean build | Clean checkout, deterministic Gradle build | Hard gate |
| Domain/JVM | Outcome, policy, scope, idempotency and state-transition tests | Hard gate |
| Journal | Atomic intent, encrypted fields, migrations, tamper/decrypt failure | Hard gate |
| Crash recovery | Crash at every send/ack boundary; lookup-only recovery | Hard gate |
| Duplicate protection | Repeated taps, concurrent requests, same-key/different-fingerprint | Hard gate |
| Live-head validation | Same-head success; moved-head conflict; missing/contradictory data | Hard gate |
| Authentication | Per-operation tier, no-biometric path, no silent downgrade | Hard gate |
| Key lifecycle | Invalidated key, removed lock, re-enrolment and old-key handling | Hard gate |
| Architecture | No secret, shell, bypass or Lab leakage | Hard gate |
| Operation integration | All seven Gate 3 operation categories | Hard gate |
| CI enforcement | Required jobs fail closed and retain evidence | Hard gate |
| Evidence dossier | Traceable task closure and explicit operator gate approval | Hard gate |

### 5.1 Mandatory operation coverage

| Operation | Positive path | Refusal/conflict | Lost acknowledgement/recovery | Live scope/head check |
|---|---:|---:|---:|---:|
| Publish | Required | Required | Required | Required |
| Upload | Required | Required | Required | Required |
| Reconcile | Required | Required | Required | Required |
| Halt | Required | Required | Required | Contract-defined |
| Resume | Required | Required | Required | Contract-defined |
| Decide | Required (contract §3.18) | Required | Required | Contract-defined |
| Task-status transition | Required (contract §3.17) | Required | Required | Contract-defined |

If an operation is not implemented, or implemented without the contract questions in PR3-003 being settled, the row cannot be marked passed.

---

## 6. Phase 3 scoring model — minimum 90/100

The score measures **verified Phase 3 readiness**, not lines of code, task count or implementation effort. The model is a [REG+] addition: the plan's Gate 3 is pass or fail. The operator may adopt the score as the acceptance measure or use it only as a progress indicator.

| Category | Weight |
|---|---:|
| A. Baseline, dependency and contract readiness | 8 |
| B. Journal, encryption, idempotency and recovery | 27 |
| C. Complete privileged operation paths | 30 |
| D. Confirmation, key lifecycle and architecture security | 15 |
| E. Android runtime, integration, CI and evidence | 20 |
| **Total** | **100** |

### 6.1 Scoring rules

1. A task earns credit only when its acceptance criteria and evidence are reviewed.
2. Partial implementation may receive partial category credit, but does not satisfy a mandatory gate.
3. A report written by the implementation agent is supporting evidence, not independent acceptance.
4. JVM tests cannot earn Android-runtime points.
5. Mock-only tests cannot earn controlled-gateway integration points.
6. Documentation cannot compensate for missing code or failing tests.
7. No category may exceed its assigned weight.
8. The score is calculated by the reviewer from the evidence dossier, not self-reported by the implementer.

### 6.2 Hard-gate score caps

Any of the following caps the Phase 3 score at **89/100 or below**, regardless of the weighted subtotal:

- Any Gate 3 operation is missing, scaffolded or falsely represented as functional.
- Any privileged write path bypasses the journal or authorized capability-minting path.
- The application can automatically resend an operation with an uncertain remote outcome.
- The app can report success without required gateway and live-head verification.
- Any capability/signature/nonce/private key/raw credential is persisted or exposed in logs.
- Any operation can silently downgrade its required authentication tier.
- **[REG+]** The Android Keystore/Room/WorkManager recovery path lacks actual Android runtime evidence.
- Any required operation integration test is absent or failing.
- `decide` or status transition is not implemented on the client, or is implemented in a way that contradicts the published schemas.
- Required CI checks can pass despite a failed required test.
- The Phase 3 evidence dossier or operator Gate 3 approval is absent.

### 6.3 Target outcome

The register is designed for a **95/100 target** and a **90/100 minimum acceptance threshold**. A 90+ score is awarded only when:

- All mandatory tasks are accepted.
- All hard gates pass.
- The evidence dossier is complete.
- The operator accepts Gate 3.

If all tasks are marked complete but any hard gate fails, the score remains capped below 90.

---

## 7. Stage 4 entry criteria

Stage 4 may begin only when all of the following are true:

- [ ] PR3-001 through PR3-044 have been reviewed and assigned a final disposition.
- [ ] Every mandatory task is `ACCEPTED`.
- [ ] Any approved deferral is explicitly outside Gate 3 and does not affect a required operation or security invariant.
- [ ] All seven Gate 3 operation categories are proven against the controlled gateway.
- [ ] The complete crash/recovery matrix passes.
- [ ] Actual Android Keystore, Room and WorkManager instrumentation tests pass.
- [ ] Authentication-tier and key-invalidation tests pass.
- [ ] Architecture and secret-hygiene checks pass.
- [ ] Required CI jobs are green and retained.
- [ ] `docs/release/PHASE-3-EVIDENCE.md` is complete.
- [ ] `docs/STATUS.md` and `RELEASE-PLAN.md` accurately reflect Phase 3 completion.
- [ ] The operator records explicit Gate 3 acceptance.
- [ ] Verified Phase 3 score is at least 90/100.

**No automatic transition:** Completion of the last code task does not itself authorize Stage 4. The gate requires evidence review and operator acceptance.

---

## 8. Implementation and change-control instructions

1. This register is installed as `docs/release/PHASE-3-REMEDIATION-REGISTER.md` by a Claude branch and pull request that the operator merges. Merging it adopts the register and states, at merge, whether the [REG+] items of §1.3 are accepted into Gate 3.
2. Do not overwrite `RELEASE-PLAN.md` or silently renumber its existing task IDs. If the operator accepts [REG+] items into Gate 3, `RELEASE-PLAN.md` is revised by a separate operator-approved change that adds a pointer to this register.
3. Use the `PR3-*` IDs in commit messages, agent reports, pull request descriptions and evidence reports. The AI Studio agent pushes to `main` (ADR 0010) and does not use branches; Claude-owned changes use branches and pull requests.
3a. The register authorizes no implementation. Work still runs under plan rules G-01, G-02, G-13 and G-14: one stage-sized run at a time, each with a prompt whose **Files** line names the paths it may touch, protected paths (`.github/workflows/*.yml`, `proguard-rules.pro`, `AndroidManifest.xml`, `app/build.gradle.kts`, `version.properties`, `build-logs/**`, `gradle/libs.versions.toml`, `network_security_config.xml`) changed only by Claude through a branch and pull request, and nothing under `gateway/**` touched by the Android agent.
4. Keep each remediation PR narrowly scoped. Do not combine gateway implementation, Phase 4 UI completion or later release machinery into these changes.
5. Preserve existing operator work and unrelated application files.
6. Each PR must state the register IDs it closes, files changed, tests run, evidence produced and any remaining limitation.
7. Do not mark a task accepted from static inspection alone where runtime or integration evidence is specified.
8. If a task reveals a contract ambiguity, stop that operation's implementation and record the blocker. Do not guess an endpoint or payload.
9. Any change to the register's acceptance criteria requires an explicit operator-approved revision.
10. Recalculate the Phase 3 score only after evidence review; do not carry forward any earlier provisional score as a measured implementation score after changes.

---

## 9. Explicit exclusions and residual production requirements

This register closes Phase 3 only. Even after a 90+ Phase 3 score and Gate 3 acceptance, the following remain outside its claim:

- Phase 2 deployment-specific endpoint, certificate-pin and connectivity acceptance.
- Phase 4 complete operator interface, dashboard, plans/tasks, governance/audit views and polish.
- Phase 5 Lab isolation and release exclusion.
- Phase 6 Class B workflow coverage.
- Phase 7 release signing, provenance, vulnerability checks, release APK verification, A03 Core acceptance and end-to-end release evidence.
- Phase 8 publication and operational retirement requirements.

Accordingly, **90+ under this register means Phase 3 is ready to hand off to Stage 4. It does not mean the complete application is ready for production release.**

---

## 10. Final acceptance record

Complete this section only after review.

| Field | Acceptance value |
|---|---|
| Reviewed commit SHA | |
| Register revision | `1.1` |
| Mandatory tasks accepted | |
| Approved deferrals | |
| Open blockers | |
| Required CI run(s) | |
| Android device/emulator evidence | |
| Controlled-gateway test evidence | |
| Verified Phase 3 score | |
| Gate 3 decision | `PENDING` |
| Operator reviewer | |
| Acceptance date (UTC) | |
| Notes / conditions | |

**Decision rule:** `Gate 3 = ACCEPTED` only when the verified score is at least 90/100, every hard gate passes, no mandatory task remains open, and the operator has signed the acceptance record.

---

---

## Appendix A — Verification log and corrections (revision 1.1)

Verification was by static inspection of `inscope-labs/operator-console` at `7fdd304`. No build, test run, device or gateway was exercised.

| Location | Correction | Reason |
|---|---|---|
| Header | Added revision, install path and verification head; replaced "implementation PRs should use review branches" | ADR 0010 and plan rule G-03: the AI Studio agent pushes to `main`; Claude changes use branches and PRs |
| §2 | Replaced the unverified list with a verified status table; added `proguard-rules.pro` finding | Findings 4, 6, 7 and 9 were wrong or incomplete (see table) |
| §1.3 | New provenance section | The register mixed plan requirements with stricter additions without saying so |
| PR3-003, PR3-025, §5.1, §6.2 | Removed the premise that the contract has no `decide` endpoint | Contract revision 1.1 §3.18 and `decide.schema.json` define it; the real gaps are the missing Android client and the attribution/rationale/evidence mapping |
| PR3-026 | Noted that §3.17 defines status transition and the client lacks it | `GatewayApi.kt` has no status-transition method |
| PR3-027 | Changed from an open scope question to a documented deferral under BD-1.3 option B | The plan's recommended default; the contract has no `order` schema (I-10) |
| PR3-022, PR3-024 | Corrected traceability: upload is T3-403, halt/resume is T3-401 | The plan's task numbering |
| PR3-021, PR3-022, PR3-023, PR3-024 | Added requirements the plan states and the register omitted (T3-303 tests, document picker and no storage permission, size cap and `cbridge` pre-checks, original preserved, `MERGED:`/`CLOSED:` display, resume requires recorded cause) | Parity with plan tasks T3-303, T3-401 to T3-403 |
| PR3-002 | Added protected-file provenance check | Commit `7fdd304` edited `proguard-rules.pro` outside the `[CLAUDE]` path |
| PR3-010 | Tagged the associated-data requirement [REG+] and noted the current cipher sets none | Not in the plan; confirmed by static search |
| PR3-030 | Stated the BD-3.1 assumption (option a) | BD-3.1 is a plan default; the operator's closure must be cited |
| PR3-040, PR3-041, PR3-043 | Tagged [REG+]; PR3-043 marked `[CLAUDE]`, protected workflow files | Plan Stage 7.1 and 7.2 own these; workflows are protected |
| PR3-042 | Added the controlled-gateway environment dependency | Gateway deployment state is not established |
| PR3-044 | Confirmed the evidence path; warned that existing completion reports are Gateway A's | Plan evidence convention |
| §8.3a, §8.10 | Added G-01/G-02/G-13/G-14 constraints; removed the unsourced "32/100" | No source for the number; the register cannot authorize work outside the plan's run rules |

## Appendix B — Executor map

| Task | Plan stage | Executor |
|---|---|---|
| PR3-001, PR3-044 | Gate 3 evidence | `[AIS]` documents; operator accepts |
| PR3-002 | 3.1 | `[CLAUDE]` review; protected files by branch and PR |
| PR3-003 | Contract | `[CLAUDE]` docs-only PR; operator approves; gateway agent implements any gateway change |
| PR3-010 to PR3-015 | 3.2 | `[AIS]` |
| PR3-020, PR3-021 | 3.3 | `[AIS]` |
| PR3-022 to PR3-027 | 3.4 | `[AIS]` |
| PR3-030 to PR3-032 | 3.5 | `[AIS]` |
| PR3-033 | 3.6 | `[AIS]` |
| PR3-040 to PR3-042 | Gate 3 evidence | `[AIS]` tests; the controlled gateway is an operator-named environment |
| PR3-043 | 7.1 (pulled forward) | `[CLAUDE]` branch and PR |

*End of register.*

