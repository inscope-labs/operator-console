# Phase 3 Contract-to-Client Traceability Matrix (`PR3-003`)

- **Contract Revision:** `Revision 1.1 (BD-1.7, ADR 0013)` — [`docs/gateway-contract.md`](../gateway-contract.md)
- **Schema Registry:** `OperatorGatewaySchemaRegistry` `version 1.0.0` (22 schema files) — [`docs/schemas/index.json`](../schemas/index.json)
- **Operator Decisions:** [`docs/adr/0014-phase-3-operator-decisions.md`](../adr/0014-phase-3-operator-decisions.md) (`D1`–`D5`)
- **Workspace HEAD SHA:** `unverified (no .git metadata in sandbox)`
- **Verification Scope:** Android client (`GatewayEndpoints`, `GatewayApi`, `GatewayRepository`, `core/network/dto/*`, `ErrorMapper`, `OperationRepositoryImpl`) vs frozen contract Revision 1.1 and `docs/schemas/*.schema.json`. Gateway implementation status is out of scope.
- **Closure Note:** `PR3-003` does **not** close in this run; `D1` (work-order ID on task read) and `D3` (`POST /plans/{id}/status` server route) require a separate contract Revision 1.2 and gateway change.

---

## 1. Status Vocabulary & Summary Counts

Closed status set: `matched` | `client-missing` | `contract-only` (not consumed by the Android app) | `client-only` (no contract route) | `mismatch`.

| Scope | `matched` | `mismatch` | `contract-only` | `client-missing` | `client-only` |
|---|---|---|---|---|---|
| **Contract §3 endpoints (§3.1–§3.21, incl. §3.15a & both §3.20 routes = 23 rows)** | **20** | **1** (`§3.16`) | **2** (`§3.20` `/audit`, `/audit/{ref}`) | **0** | **0** *(1 dead route `DECISION` removed in PR3-003 Task D)* |
| **Contract §2-only route (`POST /plans/{id}/order`, no §3/schema)** | — | — | **1** *(excluded per ADR 0014 D5)* | — | — |
| **Contract §4.1 Error Registry (43 contract codes vs 41 `ErrorMapper` codes)** | **40** | **0** | — | **3** *(missing in `ErrorMapper.GATEWAY_CODES`; present in `OperationRepositoryImpl`)* | **1** (`NOT_FOUND` in `ErrorMapper` only) |

---

## 2. Endpoint Traceability Matrix (§3.1 to §3.21 + §2 `order` & Removed `DECISION`)

| Contract Section | Method & Path | Schema File(s) | `GatewayEndpoints` Constant / Fn | `GatewayApi` / Repo Method | Request DTO | Response DTO | Error Mapping | Status | Notes / Unconsumed Fields & Findings |
|---|---|---|---|---|---|---|---|---|---|
| §3.1 | `GET /health` | `health.schema.json` | `HEALTH` / `health()` | `GatewayRepository.health()` *(not on `GatewayApi`)* | *(none)* | `GatewayHealthDto` | `ErrorMapper` (`map`, transport mappers) | `matched` | Schema field `config` is omitted by `GatewayHealthDto` (**not consumed**). |
| §3.2 | `GET /plans` | `plans-list.schema.json` | `PLANS` / `plans()` | `GatewayApi.listPlans()` *(also backs `getPlan()`)* | *(none)* | `List<PlanSummaryDto>` | `ErrorMapper` via callers | `matched` | All schema fields consumed. |
| §3.3 | `GET /plans/{id}/status` | `plan-status.schema.json` | `PLAN_STATUS` / `planStatus(planId)` | `GatewayApi.planStatus(planId)` | *(none)* | `PlanStatusDto` | `ErrorMapper` via callers | `matched` | Shares `PLAN_STATUS` path constant with §3.17 `POST`. |
| §3.4 | `POST /plans/{id}/validate` | `plan-validate.schema.json` | `PLAN_VALIDATE` / `planValidate(planId)` | `GatewayApi.validatePlan(planId)` | `"{}"` literal *(schema `{}`)* | `PlanValidationDto` | `ErrorMapper` via callers | `matched` | All schema fields consumed. |
| §3.5 | `GET /plans/{id}/render-check` | `plan-render-check.schema.json` | `PLAN_RENDER_CHECK` / `planRenderCheck(planId)` | `GatewayApi.renderCheck(planId)` | *(none)* | `RenderCheckDto` | `ErrorMapper` via callers | `matched` | All schema fields consumed. |
| §3.6 | `GET /tasks/next` | `tasks-next.schema.json` | `TASKS_NEXT` / `tasksNext()` | `GatewayApi.nextTasks()` | *(none)* | `List<TaskSummaryDto>` | `ErrorMapper` via callers | `matched` | **Known gap (`ADR 0014 D1`, ratchet baseline):** `tasks-next.schema.json` (rev 1.1) and `TaskSummaryDto` lack work-order identifier; resolved in future contract rev 1.2. |
| §3.7 | `GET /branches/{name}/head` | `branch-head.schema.json` | `BRANCH_HEAD` / `branchHead(branchName)` | `GatewayApi.branchHead(branch)` | *(none)* | `BranchHeadDto` | `ErrorMapper` / `verifyLiveHead` | `matched` | All schema fields consumed. |
| §3.8 | `POST /tasks/{id}/publish` | `publish.schema.json` | `PUBLISH` / `publish(taskId)` | `GatewayApi.publish(request)` | `PublishRequestDto` | `GatewayAckDto` | `OperationRepositoryImpl` (`KNOWN_GATEWAY_CODES`, `CONFLICT_CODES`) | `matched` | Request includes `idempotencyKey`; `GatewayAckDto.outcome` defaulted `""` (populated only by `reconcile`). |
| §3.9 | `POST /tasks/{id}/reconcile` | `reconcile.schema.json` | `RECONCILE` / `reconcile(taskId)` | `GatewayApi.reconcile(request)` | `ReconcileRequestDto` | `GatewayAckDto` | `OperationRepositoryImpl` (`KNOWN_GATEWAY_CODES`, `CONFLICT_CODES`) | `matched` | Request includes `idempotencyKey`; all response fields consumed. |
| §3.10 | `POST /tasks/{id}/upload` | `upload.schema.json` | `UPLOAD` / `upload(taskId)` | `GatewayApi.upload(request)` | `UploadRequestDto` | `GatewayAckDto` | `OperationRepositoryImpl` (`KNOWN_GATEWAY_CODES`, `CONFLICT_CODES`) | `matched` | Request includes `idempotencyKey`; `GatewayAckDto.outcome` defaulted `""`. |
| §3.11 | `POST /halt` | `halt.schema.json` | `HALT` / `halt()` | `GatewayApi.halt(request)` | `EngineScopeRequestDto` | `GatewayAckDto` | `OperationRepositoryImpl` (`KNOWN_GATEWAY_CODES`, `CONFLICT_CODES`) | `matched` | Request includes `idempotencyKey`; `GatewayAckDto.outcome` defaulted `""`. |
| §3.12 | `POST /resume` | `resume.schema.json` | `RESUME` / `resume()` | `GatewayApi.resume(request)` | `EngineScopeRequestDto` | `GatewayAckDto` | `OperationRepositoryImpl` (`KNOWN_GATEWAY_CODES`, `CONFLICT_CODES`) | `matched` | Request includes `idempotencyKey`; `GatewayAckDto.outcome` defaulted `""`. |
| §3.13 | `POST /sync` | `sync.schema.json` | `SYNC` / `sync()` | `GatewayApi.sync(request)` | `SyncRequestDto` | `SyncStatusDto` | `ErrorMapper` via callers | `matched` | Read-only tracking sync; `idempotencyKey` not required by §1.6/schema. |
| §3.14 | `GET /doctor` | `doctor.schema.json` | `DOCTOR` / `doctor()` | `GatewayApi.doctor()` | *(none)* | `DoctorReportDto` (`DoctorCheckDto`) | `ErrorMapper` via callers | `matched` | All schema fields consumed. |
| §3.15a | `POST /enroll/challenge` | `enroll-challenge.schema.json` | `ENROLL_CHALLENGE` / `enrollChallenge()` | `GatewayApi.enrollChallenge()` | `"{}"` literal | `EnrollChallengeDto` | `ErrorMapper` via enrollment use cases | `matched` | Single-use attestation challenge; `idempotencyKey` not required. |
| §3.15 | `POST /enroll` | `enroll.schema.json` | `ENROLL` / `enroll()` | `GatewayApi.enroll(request)` | `EnrollRequestDto` | `EnrollResponseDto` | `ErrorMapper` via enrollment use cases | `matched` | Request/response match `enroll.schema.json`; `idempotencyKey` not required. |
| §3.16 | `POST /enroll/rotate` | `enroll-rotate.schema.json` | `ENROLL_ROTATE` / `enrollRotate()` | `GatewayApi.rotateKey(request)` | `RotateKeyRequestDto` | `EnrollResponseDto` | `ErrorMapper` via rotation use case | `mismatch` | **Finding (ratchet baseline):** `enroll-rotate.schema.json` response requires `rotatedAt`, but `GatewayApi.rotateKey` returns `EnrollResponseDto` (`enrolledAt = 0L`, ignoring `rotatedAt`). `rotatedAt` is **not consumed** and `RotateDeviceKeyUseCase` reads `0L` from the wire. |
| §3.17 | `POST /plans/{id}/status` | `plan-status-transition.schema.json` | `PLAN_STATUS` / `planStatus(planId)` | `GatewayApi.transitionPlanStatus(request)` | `PlanStatusTransitionRequestDto` | `PlanStatusTransitionAckDto` | `OperationRepositoryImpl` (`KNOWN_GATEWAY_CODES`, `CONFLICT_CODES`) | `matched` | Request includes `idempotencyKey`; `ADR 0014 D3` tracks gateway implementation of `POST` route. |
| §3.18 | `POST /plans/{id}/decide` | `decide.schema.json` | `PLAN_DECIDE` / `planDecide(planId)` | `GatewayApi.decide(request)` | `DecideRequestDto` | `DecideAckDto` | `OperationRepositoryImpl` (`KNOWN_GATEWAY_CODES`, `CONFLICT_CODES`) | `matched` | Request/response match `decide.schema.json` (`ADR 0014 D2`); see §5 Q1–Q3. |
| §3.19 | `GET /operations/{id}` | `operation-lookup.schema.json` | `OPERATION_LOOKUP` / `operationLookup(operationId)` | `GatewayApi.lookupOperation(idempotencyKey)` | *(none)* | `OperationLookupDto` | `OperationRepositoryImpl` (`handleLookupHttpException` / `mapLookupDtoToOutcome`) | `matched` | Schema fields match; `ErrorMapper` itself lacks rev 1.1 codes (handled in `OperationRepositoryImpl.KNOWN_GATEWAY_CODES`). |
| §3.20 | `GET /audit` | *(none)* | *(none)* | *(none)* | *(none)* | *(none)* | *(none)* | `contract-only` | Not consumed by the Android app; no schema in `docs/schemas/index.json`. |
| §3.20 | `GET /audit/{ref}` | *(none)* | *(none)* | *(none)* | *(none)* | *(none)* | *(none)* | `contract-only` | Not consumed by the Android app; no schema in `docs/schemas/index.json`. |
| §3.21 | `POST /admin/revoke` | `admin-revoke.schema.json` | `ADMIN_REVOKE` / `adminRevoke()` | `GatewayApi.revokeKey(request)` | `RevokeKeyRequestDto` | `RevokeKeyResponseDto` | `ErrorMapper` via `RevokeDeviceKeyUseCase` | `matched` | Key-management endpoint; `idempotencyKey` not required. |
| §2 table | `POST /plans/{id}/order` | *(none — no §3 spec)* | *(none)* | *(none)* | *(none)* | *(none)* | *(none)* | `contract-only` | Excluded on Android per `ADR 0014 D5` (BD-1.3 option B); `CreateWorkOrderUseCase` is `UnsupportedByContract`. |
| *(none)* | `/decisions/{decisionId}` | *(none)* | *`DECISION` / `decision()` removed* | *(none)* | *(none)* | *(none)* | *(none)* | `client-only` *(removed)* | Dead constant/helper had zero callers outside `GatewayEndpoints.kt` declaration/KDoc; deleted under `PR3-003` Task D. |

---

## 3. Contract §4.1 Error Codes vs `ErrorMapper`

Contract §4.1 defines **43** error codes (39 table rows, 4 containing `/` aliases). `ErrorMapper.GATEWAY_CODES` defines **41** codes. Note that `ErrorMapper.stateFor` fails closed to `GatewayFailureState.UNKNOWN` (and `map` returns `AppResult.Failure(code = "unknown", ...)`).

| Code | Contract §4.1 HTTP Status | `ErrorMapper.GATEWAY_CODES` Mapping | `OperationRepositoryImpl` Handling | Parity Status | Finding / Notes |
|---|---|---|---|---|---|
| `UNKNOWN_KID` | 401 | `AUTHENTICATION` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `BAD_SIGNATURE` | 401 | `AUTHENTICATION` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `OPERATOR_AUTH` | 401 | `AUTHENTICATION` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `CREDENTIAL_NOT_ACCEPTED` | 400 | `AUTHENTICATION` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `DEVICE_NOT_ENROLLED` | 401 / 403 | `SCOPE_REFUSED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `NOT_ENROLLED` | 401 / 403 | `SCOPE_REFUSED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `ENROLL_CHALLENGE_INVALID` | 400 | `FORMAT` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `ENROLL_ATTESTATION_INVALID` | 400 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `AUTH_TIER_NOT_ALLOWED` | 403 | `SCOPE_REFUSED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `BRANCH_MISMATCH` | 403 | `SCOPE_REFUSED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `ENVIRONMENT_MISMATCH` | 403 | `ENVIRONMENT_MISMATCH` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `CAPABILITY_EXPIRED` | 401 / 400 | `FORMAT` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `EXPIRED` | 401 / 400 | `FORMAT` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `TTL_TOO_LONG` | 400 | `FORMAT` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `UNKNOWN_OP` | 400 | `SCOPE_REFUSED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `BAD_FIELD_COUNT` | 400 | `FORMAT` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `BAD_ENCODING` | 400 | `FORMAT` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `BRANCH_NOT_TASK` | 400 | `SCOPE_REFUSED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `BRANCH_INVALID` | 400 | `SCOPE_REFUSED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `BRANCH_MISSING` | 404 | `NOT_FOUND` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `IDEMPOTENCY_KEY_INVALID` | 400 | *(absent — falls back to `UNKNOWN`)* | `KNOWN_GATEWAY_CODES` (`Refused` / `RecoveryRequired`) | `client-missing` *(in `ErrorMapper`)* | **Ratchet finding:** Revision 1.1 BD-1.7 code not yet added to `ErrorMapper.GATEWAY_CODES`. |
| `IDEMPOTENCY_KEY_REUSED` | 409 | *(absent — falls back to `UNKNOWN`)* | `KNOWN_GATEWAY_CODES` + `CONFLICT_CODES` (`Conflict`) | `client-missing` *(in `ErrorMapper`)* | **Ratchet finding:** Revision 1.1 BD-1.7 code not yet added to `ErrorMapper.GATEWAY_CODES`. |
| `OPERATION_NOT_FOUND` | 404 | *(absent — falls back to `UNKNOWN`)* | `KNOWN_GATEWAY_CODES` (`RecoveryRequired` on lookup 404) | `client-missing` *(in `ErrorMapper`)* | **Ratchet finding:** Revision 1.1 BD-1.7 code not yet added to `ErrorMapper.GATEWAY_CODES`. |
| `NONCE_REPLAYED` | 409 | `REPLAYED` | `CONFLICT_CODES` (`Conflict`) | `matched` | — |
| `HEAD_MOVED` | 409 | `HEAD_DRIFTED` | `CONFLICT_CODES` (`Conflict`) | `matched` | — |
| `HEAD_DRIFT` | 409 | `HEAD_DRIFTED` | `CONFLICT_CODES` (`Conflict`) | `matched` | — |
| `HALT_ACTIVE` | 423 | `HALT_ACTIVE` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `REPO_NOT_PRIVATE` | 403 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `ANCESTRY_FAIL` | 409 | `GUARD_FAILED` | `CONFLICT_CODES` (`Conflict`) | `matched` | — |
| `DELTA_EMPTY` | 400 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `MERGE_COMMITS` | 400 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `DENIED_PATH` | 403 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `SECRET_IN_DIFF` | 403 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `SUBMISSION_IMMUTABLE` | 409 | `CONFLICT` | `CONFLICT_CODES` (`Conflict`) | `matched` | — |
| `GUARD_FAILED` | 422 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `UNKNOWN_REPO` | 400 | `FORMAT` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `RULESET_REJECTED` | 403 | `GUARD_FAILED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `RECONCILE_REQUIRED` | 409 | `CONFLICT` | `CONFLICT_CODES` (`Conflict`) | `matched` | — |
| `TOKEN_UNAVAILABLE` | 503 | `GATEWAY_ERROR` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `RATE_BUDGET_LOW` | 429 | `RATE_LIMITED` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `NETWORK` | 502 | `GATEWAY_ERROR` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `CONFIG_INVALID` | 503 | `GATEWAY_ERROR` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `NOT_IMPLEMENTED` | 501 | `GATEWAY_ERROR` | `KNOWN_GATEWAY_CODES` (`Refused`) | `matched` | — |
| `NOT_FOUND` | *(absent from §4.1)* | `NOT_FOUND` | *(via `ErrorMapper.GATEWAY_CODES`)* | `client-only` *(in `ErrorMapper`)* | **Ratchet finding:** Legacy code in `ErrorMapper.GATEWAY_CODES`; §4.1 uses `BRANCH_MISSING` and `OPERATION_NOT_FOUND`. |

---

## 4. Schema Response Fields Not Consumed by Client DTOs

| Endpoint / Schema | Schema Property | DTO Class | Disposition |
|---|---|---|---|
| §3.1 `GET /health` (`health.schema.json`) | `config` (`{valid, missing}`) | `GatewayHealthDto` | **not consumed** — ignored via `Json { ignoreUnknownKeys = true }` |
| §3.16 `POST /enroll/rotate` (`enroll-rotate.schema.json`) | `rotatedAt` | `EnrollResponseDto` | **not consumed / mismatch** — `GatewayApi.rotateKey` reuses `EnrollResponseDto` (`enrolledAt`), which reads `0L` instead of `rotatedAt` |
| §3.6 `GET /tasks/next` (`tasks-next.schema.json`) | *(work-order identifier not yet in rev 1.1)* | `TaskSummaryDto` | **Known gap (`ADR 0014 D1`)** — pending contract Revision 1.2 + gateway update |

---

## 5. Answers to the Three `PR3-003` Contract Questions

### Q1. `decide` Fields (`ADR 0014 D2`)
- **Contract / ADR Resolution:** Per **ADR 0014 D2**, `POST /plans/{id}/decide` (§3.18, `decide.schema.json`) uses a **documented reduction** from the original `RELEASE-PLAN.md` T3-404 UI fields (`attribution`, `rationale`, `scope`, `evidence`):
  - The request sends `decision` and `notes` plus the schema's scope/idempotency/capability fields (`plan`, `task`, `branch`, `idempotencyKey`, `capability`).
  - Operator **rationale** travels in `notes`.
  - **Attribution** is recorded by the gateway as the enrolled key identifier (`kid`) from the signed capability in its audit record.
  - No separate `evidence` field exists on the wire; any evidence reference the operator records travels in `notes`.
- **DTO Verification (`DecideRequestDto` vs `decide.schema.json`):** Exact 1:1 match (`plan`, `task`, `branch`, `decision`, `notes`, `idempotencyKey`, `capability`; required `plan`, `decision`, `idempotencyKey`, `capability`; `additionalProperties: false`). Zero field differences.
- **Read-only code note (`DecideFrameworkUseCase.kt`):** `DecideFrameworkUseCase` still defaults `contractSchemaSettled: Boolean = false` (returning `DecideResult.ContractBlocked`) until `PR3-025` updates it to reflect ADR 0014 D2 and the UI reduction.

### Q2. "Live Check" for `decide` and `status-transition` (`OperationRepositoryImpl.kt` read-only)
1. **`decide` (`OperationRepositoryImpl.decide` + `handleWriteAcknowledgement`):**
   - `decide.schema.json` (`definitions.response`) returns `accepted`, `operation`, `plan`, `decision`, `auditRef` — **no `branch` and no `headSha`**.
   - `OperationRepositoryImpl.decide` adapts `DecideAckDto` into `GatewayAckDto` with `branch = request.scope.branch`, `headSha = ""`, `outcome = ack.decision`, `auditRef = ack.auditRef`.
   - `handleWriteAcknowledgement` immediately hits `if (!ack.accepted || ack.auditRef.isBlank() || ack.headSha.isBlank())` and **always transitions the journal entry to `UNCERTAIN`, returning `OperationOutcome.UncertainCompletion`** (never `Succeeded`), even when `accepted == true` and `auditRef` is non-blank.
   - **I-06 / ADR 0014 Gap:** It obeys I-06 fail-closed ("never claim false success without live verification"), but leaves `decide` permanently stuck in `UncertainCompletion` on write because there is no `headSha` to verify against `GET /branches/{name}/head`. Reaching `Succeeded` for `decide` requires a domain-appropriate live-verification rule (e.g., plan/audit confirmation) rather than a branch-head SHA check.
2. **`status-transition` (`OperationRepositoryImpl.setTaskStatus` + `handleWriteAcknowledgement`):**
   - **Schema correction:** Unlike `decide`, frozen `plan-status-transition.schema.json` (`definitions.response`) **does require `headSha`** (alongside `accepted`, `operation`, `plan`, `task`, `newStatus`, `auditRef`); it omits only `branch`.
   - `OperationRepositoryImpl.setTaskStatus` adapts `PlanStatusTransitionAckDto` into `GatewayAckDto` with `branch = request.scope.branch`, `headSha = ack.headSha`, `outcome = ack.newStatus`, `auditRef = ack.auditRef`.
   - `handleWriteAcknowledgement` runs `verifyLiveHead(key, intentBranch, ack.headSha)` via `GET /branches/{name}/head` and marks `TERMINAL_SUCCEEDED` (`OperationOutcome.Succeeded`) when `live.headSha` equals `ack.headSha` (otherwise `UncertainCompletion`).
   - **I-06 / ADR 0014 Gap:** `OperationRepositoryImpl` verifies branch head SHA only and does **not** verify authoritative plan/task status (`GET /plans/{id}/status` / `GET /tasks/next`) before declaring `Succeeded` (although `SetTaskStatusUseCase` performs a post-success best-effort refresh of `taskRepository.getTask` / `planRepository.getStatus` after `Succeeded` is already decided). Also, `POST /plans/{id}/status` is not yet implemented on Gateway A (`ADR 0014 D3`).

### Q3. Lookup (`GET /operations/{id}`) Reporting & Client Mapping for `decide` and `status-transition`
- **Contract §3.19 & `operation-lookup.schema.json`:**
  - `GET /operations/{id}` is keyed by the 32-hex `idempotencyKey` and returns `status` ∈ `{pending, completed, failed, refused}` (or HTTP `404 OPERATION_NOT_FOUND` / `400 IDEMPOTENCY_KEY_INVALID`).
  - When `status == "completed"`, contract §3.19 and `operation-lookup.schema.json` (`allOf`) **require `auditRef` and `headSha`**; `branch` is optional; when `status` is `failed` or `refused`, `code` (from §4.1) is required.
  - Contract §4.3 requires `completed` -> `Succeeded` only after live-head check (otherwise `UncertainCompletion`); `pending` -> `UncertainCompletion`; `refused` -> `Conflict` for the 7 conflict codes or `Refused` otherwise; `failed` / `404 OPERATION_NOT_FOUND` -> `RecoveryRequired`.
- **Client Mapping Coverage (`OperationRepositoryImpl.mapLookupDtoToOutcome`):**
  - `pending`, `refused` (via `CONFLICT_CODES` and `KNOWN_GATEWAY_CODES`, which explicitly include `IDEMPOTENCY_KEY_INVALID`, `IDEMPOTENCY_KEY_REUSED`, `OPERATION_NOT_FOUND`), `failed`, `404 OPERATION_NOT_FOUND`, unknown `status`, and missing/unknown `code` are all mapped according to §4.3 for **both** `decide` and `status-transition`.
  - For `completed`: `mapLookupDtoToOutcome` requires non-blank `auditRef` AND `headSha` (failing closed to `RecoveryRequired` if either is missing), resolves `branch` from `lookup.branch ?: decryptedRecord?.intent?.branch`, and verifies `GET /branches/{name}/head == lookup.headSha` before marking `TERMINAL_SUCCEEDED` (`Succeeded`); drift or non-transient verification failure returns `UncertainCompletion`.
  - **Specific Gaps Found:**
    1. **For `decide`:** Because `DecideAckDto` (§3.18) carries no `headSha`, synchronous `decide` always stays `UncertainCompletion`, pushing resolution to recovery lookup. Under §3.19, if the gateway returns the scoped branch's `headSha` on `completed` lookup, recovery validates it and resolves to `Succeeded`; if the gateway omits `headSha` (since decide is plan-level), both `operation-lookup.schema.json` and `mapLookupDtoToOutcome` treat it as invalid/missing evidence (`RecoveryRequired`).
    2. **For `status-transition`:** `OperationLookupDto` has no `newStatus` field, and `mapLookupDtoToOutcome` neither inspects `lookup.operation` against the journalled intent's operation nor refreshes/verifies authoritative plan/task status on recovery.
