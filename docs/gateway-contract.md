# Operator Gateway Specification and Wire Contract (`v1`)

This document defines the HTTP API surface of the Cloudflare Operator Gateway (`cbridge` replacement)
and the contract between the Operator Console Android application and the gateway.

The gateway is the **sole enforcement point** for all repository operations. The console is untrusted
for enforcement; it functions as a scoped approval and minting client.

---

## 1. Protocol Architecture & Conventions

### 1.1 Transport & Framing
- **Protocol**: HTTPS (TLS 1.3 mandatory).
- **Default Port**: 443.
- **Content-Type**: `application/json; charset=utf-8` for all requests and responses containing a payload.
- **Character Encoding**: UTF-8 strictly required everywhere.
- **Wire Payload Format**: Standard RFC 8259 JSON.

### 1.2 Authentication & Authorization Headers
Requests sent to the gateway require two distinct authorization components:
1. **Operator Bearer Credential**:
   - Transmitted via standard HTTP header:
     ```http
     Authorization: Bearer <operator_credential>
     ```
   - Scoped strictly to the gateway administrative surface. Holds zero repository credentials.
   - Checked first on all privileged endpoints. Missing or invalid credentials return `OPERATOR_AUTH` (HTTP 401).
2. **Operator Capability**:
   - For mutating/privileged operations (`publish`, `reconcile`, `upload`, `halt`, `resume`), the signed capability is passed directly in the JSON request body field `capability`:
     ```json
     {
       "capability": "ctxop-v1\nk2\n1761840000\norder-7731\natlas\n10008\nPUBLISH\ntask-10008-refactor-parser\n9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d\n7f3c9b1e5d2a7046\nsig_base64url_raw_rs"
     }
     ```
   - For write operations, the capability body field is mandatory.

### 1.3 Timestamps & Clock
- Capability timestamps (`exp`) are **Unix seconds** (integer epoch seconds).
- Response JSON representations use integer epoch seconds for machine-readable fields (e.g. `timestamp`, `enrolledAt`) and ISO-8601 UTC strings (`YYYY-MM-DDTHH:MM:SSZ`) for human-readable audit timestamps.
- Gateway clock skew tolerance: maximum 10 seconds.
- Default Capability TTL: **300 seconds (5 minutes)**. Maximum allowed TTL: **300 seconds**. Any capability with `exp - now > 300` is rejected with `TTL_TOO_LONG`.

### 1.4 Idempotency & Replay Protection
- Capabilities are strictly single-use.
- The `nonce` (minimum 16 hex characters) is recorded in gateway fast-storage prior to executing the operation.
- Re-presenting the same capability or nonce returns `NONCE_REPLAYED` (HTTP 409).
- Read operations are idempotent.

### 1.5 Versioning & Extensibility
- Protocol version: `v1`.
- Capability domain separator: `ctxop-v1`.
- The client must ignore unknown JSON fields on gateway responses.
- The gateway must reject extra or unexpected fields in capability messages (`BAD_FIELD_COUNT`).

---

## 2. cbridge Command Mapping

The gateway directly replaces the legacy CLI `cbridge` while eliminating repository credentials from local machines.

| `cbridge` Command | Gateway Endpoint | Scope | Capability Required | Notes |
|---|---|---|---|---|
| `publish` | `POST /tasks/{id}/publish` | Task branch (`^task-`) | Yes | Runs task guard G1–G13 |
| `upload` | `POST /tasks/{id}/upload` | Task branch (`^task-`) | Yes | Uploads submission archive |
| `reconcile` | `POST /tasks/{id}/reconcile` | Task branch (`^task-`) | Yes | Merges/reconciles completed task |
| `halt` | `POST /halt` | Engine branch (`engine`) | Yes (Engine scoped) | Global emergency stop |
| `resume` | `POST /resume` | Engine branch (`engine`) | Yes (Engine scoped) | Resumes engine processing |
| `status` | `GET /plans/{id}/status` | Read-only | No | Returns plan status |
| `sync` / `verify-main` | `POST /sync` | Read-only / sync | No (Bearer auth) | Fast-forwards tracking |
| `doctor` | `GET /doctor` | Read-only | No (Bearer auth) | Verifies config & rulesets |
| `init` / `bootstrap` | *EXCLUDED* | None | N/A | **Explicitly Out of Scope**; console never provisions repos |

---

## 3. Gateway Endpoints Specification

### 3.1 `GET /health`
Liveness and environment probe. Used by settings and the Lab environment safety guard.

- **Auth**: None or optional Operator Bearer.
- **Success Response**: `200 OK`
```json
{
  "status": "ok",
  "environment": "test",
  "version": "1.0.0",
  "timestamp": 1761840000
}
```
*Note: `environment` must be `"test"` or `"production"`. The Lab engine strictly refuses to run against any gateway where `environment != "test"`.*

---

### 3.2 `GET /plans`
Lists active and archived plans.

- **Auth**: Operator Bearer.
- **Success Response**: `200 OK`
```json
[
  {
    "id": "atlas",
    "title": "Atlas Engine Migration",
    "status": "in_progress",
    "taskCount": 14,
    "completedTaskCount": 8,
    "updatedAt": 1761840000
  }
]
```

---

### 3.3 `GET /plans/{id}/status`
Fetches status of a specific plan.

- **Auth**: Operator Bearer.
- **Success Response**: `200 OK`
```json
{
  "id": "atlas",
  "status": "in_progress",
  "halted": false,
  "openBranches": ["task-10008-refactor-parser"],
  "blockedTasks": [],
  "currentMilestone": "M2-Parser"
}
```

---

### 3.4 `POST /plans/{id}/validate`
Validates integrity of plan definitions.

- **Auth**: Operator Bearer.
- **Request**: `{}`
- **Success Response**: `200 OK`
```json
{
  "valid": true,
  "planId": "atlas",
  "errors": [],
  "warnings": []
}
```

---

### 3.5 `GET /plans/{id}/render-check`
Checks plan rendering and markdown output.

- **Auth**: Operator Bearer.
- **Success Response**: `200 OK`
```json
{
  "ok": true,
  "planId": "atlas",
  "renderIssues": []
}
```

---

### 3.6 `GET /tasks/next`
Lists next available unassigned or ready tasks.

- **Auth**: Operator Bearer.
- **Success Response**: `200 OK`
```json
[
  {
    "id": "10008",
    "planId": "atlas",
    "title": "Refactor parser",
    "branch": "task-10008-refactor-parser",
    "status": "ready",
    "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
  }
]
```

---

### 3.7 `GET /branches/{name}/head`
Fetches current remote head commit SHA of a given branch. Used by `VerifyBranchHeadUseCase` before minting.

- **Auth**: Operator Bearer.
- **Success Response**: `200 OK`
```json
{
  "branch": "task-10008-refactor-parser",
  "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d"
}
```
- **Error Response**: `404 Not Found` if branch does not exist.

---

### 3.8 `POST /tasks/{id}/publish`
Publishes an approved task branch to the staging pipeline.

- **Auth**: Operator Bearer + Capability.
- **Request Body**:
```json
{
  "plan": "atlas",
  "task": "10008",
  "branch": "task-10008-refactor-parser",
  "head": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
  "capability": "ctxop-v1\nk2\n1761840000\norder-7731\natlas\n10008\nPUBLISH\ntask-10008-refactor-parser\n9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d\n7f3c9b1e5d2a7046\nsig_base64url"
}
```
- **Success Response**: `200 OK`
```json
{
  "accepted": true,
  "operation": "PUBLISH",
  "branch": "task-10008-refactor-parser",
  "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
  "auditRef": "audit-20261030-98234"
}
```

---

### 3.9 `POST /tasks/{id}/reconcile`
Reconciles an open task branch after PR review.

- **Auth**: Operator Bearer + Capability.
- **Request Body**:
```json
{
  "plan": "atlas",
  "task": "10008",
  "branch": "task-10008-refactor-parser",
  "head": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
  "submissionId": "sub-7731",
  "capability": "ctxop-v1\n..."
}
```
- **Success Response**: `200 OK`
```json
{
  "accepted": true,
  "operation": "RECONCILE",
  "branch": "task-10008-refactor-parser",
  "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
  "outcome": "MERGED:pr-142",
  "auditRef": "audit-20261030-98235"
}
```

---

### 3.10 `POST /tasks/{id}/upload`
Uploads a task submission zip package.

- **Auth**: Operator Bearer + Capability.
- **Request Body**:
```json
{
  "plan": "atlas",
  "task": "10008",
  "branch": "task-10008-refactor-parser",
  "head": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
  "submissionId": "sub-7731",
  "zipSha256": "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
  "zipBase64": "UEsDBBQAAAAIA...",
  "capability": "ctxop-v1\n..."
}
```
- **Success Response**: `200 OK`
```json
{
  "accepted": true,
  "operation": "UPLOAD",
  "branch": "task-10008-refactor-parser",
  "headSha": "9f2c1b7e4a5d6038c1b2a9e7d4f6081c3b5a7e9d",
  "auditRef": "audit-20261030-98236"
}
```

---

### 3.11 `POST /halt`
Engages global halt on engine operations.

- **Auth**: Operator Bearer + Capability (`op: HALT`, `branch: engine`).
- **Request Body**:
```json
{
  "branch": "engine",
  "head": "a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e",
  "reason": "Security review in progress",
  "capability": "ctxop-v1\n..."
}
```
- **Success Response**: `200 OK`
```json
{
  "accepted": true,
  "operation": "HALT",
  "branch": "engine",
  "headSha": "a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e",
  "auditRef": "audit-20261030-98237"
}
```

---

### 3.12 `POST /resume`
Resumes engine operations from halt state.

- **Auth**: Operator Bearer + Capability (`op: RESUME`, `branch: engine`).
- **Request Body**:
```json
{
  "branch": "engine",
  "head": "a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e",
  "reason": "Audit completed",
  "capability": "ctxop-v1\n..."
}
```
- **Success Response**: `200 OK`
```json
{
  "accepted": true,
  "operation": "RESUME",
  "branch": "engine",
  "headSha": "a1b2c3d4e5f60718293a4b5c6d7e8f9a0b1c2d3e",
  "auditRef": "audit-20261030-98238"
}
```

---

### 3.13 `POST /sync`
Verifies consumer repo synchronization with upstream tracking.

- **Auth**: Operator Bearer.
- **Request Body**: `{"branch": "main"}`
- **Success Response**: `200 OK`
```json
{
  "status": "synced",
  "headSha": "b3c4d5e6f708192a3b4c5d6e7f8a9b0c1d2e3f4a",
  "inSync": true
}
```

---

### 3.14 `GET /doctor`
Health, repository access, and ruleset diagnostic report.

- **Auth**: Operator Bearer.
- **Success Response**: `200 OK`
```json
{
  "healthy": true,
  "checks": [
    { "name": "github_token_validity", "passed": true },
    { "name": "ruleset_bypass_configured", "passed": true },
    { "name": "halt_state", "passed": true, "details": "Engine active" }
  ]
}
```

---

### 3.15 `POST /enroll`
Enrols a new device public key and attestation statement.

- **Auth**: Operator Bearer.
- **Request Body**:
```json
{
  "alias": "operator-console-signing-v1",
  "publicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
  "attestationChain": [
    "MIIB...certificate1_base64...",
    "MIIB...certificate2_base64..."
  ]
}
```
- **Success Response**: `200 OK`
```json
{
  "kid": "k2",
  "status": "active",
  "enrolledAt": 1761840000
}
```

---

### 3.16 `POST /enroll/rotate`
Rotates an enrolled device key. Retires old `kid`.

- **Auth**: Operator Bearer.
- **Request Body**:
```json
{
  "oldKid": "k2",
  "newPublicKey": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
  "attestationChain": ["..."]
}
```
- **Success Response**: `200 OK`
```json
{
  "kid": "k3",
  "status": "active",
  "rotatedAt": 1761840000
}
```

---

## 4. Error Taxonomy & Status Codes

When a request is rejected, the gateway responds with standard HTTP error status codes and a structured body:
```json
{
  "code": "<MACHINE_READABLE_CODE>",
  "message": "<HUMAN_READABLE_SUMMARY>"
}
```

### 4.1 Error Code Registry

| Error Code | HTTP Status | Rejection Stage | Description |
|---|---|---|---|
| `UNKNOWN_KID` | `401 Unauthorized` | Identity | The key selector `kid` is not recognized in the gateway device registry. |
| `BAD_SIGNATURE` | `401 Unauthorized` | Signature | ECDSA or HMAC signature verification failed against the registered key. |
| `OPERATOR_AUTH` | `401 Unauthorized` | Auth | Missing or invalid `Authorization: Bearer` operator credential header. |
| `NOT_ENROLLED` | `403 Forbidden` | Enrolment | Device key attestation rejected or device has been revoked. |
| `BRANCH_MISMATCH` | `403 Forbidden` | Scope | Capability target branch does not match work order or request endpoint branch. |
| `ENVIRONMENT_MISMATCH` | `403 Forbidden` | Safety | Lab or test invocation executed against a production-configured gateway. |
| `EXPIRED` | `400 Bad Request` | Time | Capability expiry timestamp `exp` is in the past. |
| `TTL_TOO_LONG` | `400 Bad Request` | Time | Capability TTL exceeds the gateway maximum of 300 seconds. |
| `UNKNOWN_OP` | `400 Bad Request` | Format | Capability `op` is not member of closed `OperationType` enumeration. |
| `BAD_FIELD_COUNT` | `400 Bad Request` | Format | Wire message does not contain exactly 10 LF-separated fields. |
| `BAD_ENCODING` | `400 Bad Request` | Format | Contains CR (`\r`), trailing LF, invalid characters, or bad base64url padding. |
| `BRANCH_NOT_TASK` | `400 Bad Request` | Scope | Task operation branch does not match required regex `^task-[A-Za-z0-9._-]+$`. |
| `NONCE_REPLAYED` | `409 Conflict` | Replay | Nonce has already been presented within its validity window. |
| `HEAD_DRIFT` | `409 Conflict` | Concurrency | Expected head SHA in capability does not match actual remote branch HEAD. |
| `HALT_ACTIVE` | `423 Locked` | State | Global engine halt marker is active; write operations are blocked. |
| `GUARD_FAILED` | `422 Unprocessable Entity` | Guard | cbridge guard check failed (e.g. `GUARD_FAILED:G3_TOUCHES_PROTECTED_PATH`). |

---

### 4.2 Mapping to UI States in `ErrorMapper`

The console's `ErrorMapper` maps incoming errors into one of four primary UI states (plus general authentication and format errors):

```
                       ┌────────────────────────┐
                       │   Gateway Rejection    │
                       └───────────┬────────────┘
                                   │
         ┌─────────────────────────┼─────────────────────────┬─────────────────────────┐
         ▼                         ▼                         ▼                         ▼
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  Scope Refused   │      │     Replayed     │      │   Head Drifted   │      │   Halt Active    │
└──────────────────┘      └──────────────────┘      └──────────────────┘      └──────────────────┘
· BRANCH_NOT_TASK         · NONCE_REPLAYED          · HEAD_DRIFT              · HALT_ACTIVE
· BRANCH_MISMATCH
· UNKNOWN_OP
· NOT_ENROLLED
```

1. **Scope Refused**: The operation was rejected because the scope of the capability was denied (branch mismatch, invalid operation type, or device not enrolled). The user must not re-mint without reviewing scope.
2. **Replayed**: The nonce was already used. A fresh capability must be generated with biometric confirmation.
3. **Head Drifted**: The branch has advanced remotely since the operator reviewed it. The UI prompts the operator to inspect the new HEAD SHA before approving.
4. **Halt Active**: The system is halted. Write actions are locked until an authorized operator issues a RESUME.

---

## 5. Fixture Branches for Guard Conformance (Test Consumer Repository)

The test consumer repository must maintain the following fixed branch fixtures to validate gateway and Lab enforcement of `guard_task()` rules.

| Fixture Branch Name | Scenario / Guard Target | Description & Trigger Condition | Expected Result & Code |
|---|---|---|---|
| `task-fx-clean` | Clean baseline | Valid commit with single task commit, correct author, clean diff. | `200 OK` (Accepted) |
| `not-task-invalid` | Branch naming | Branch name does not start with `task-`. | `400 Bad Request` (`BRANCH_NOT_TASK`) |
| `task-fx-no-commits` | `G1_EMPTY_DIFF` | Branch has zero commits beyond upstream base. | `422 Unprocessable Entity` (`GUARD_FAILED:G1_EMPTY_DIFF`) |
| `task-fx-merge-commit` | `G2_NO_MERGE_COMMITS` | Branch contains a merge commit (`git merge`). | `422 Unprocessable Entity` (`GUARD_FAILED:G2_NO_MERGE_COMMITS`) |
| `task-fx-touch-engine` | `G3_PROTECTED_PATH` | Diff modifies files in `engine/`. | `422 Unprocessable Entity` (`GUARD_FAILED:G3_PROTECTED_PATH`) |
| `task-fx-touch-plans` | `G3_PROTECTED_PATH` | Diff modifies files in `plans/`. | `422 Unprocessable Entity` (`GUARD_FAILED:G3_PROTECTED_PATH`) |
| `task-fx-touch-github` | `G3_PROTECTED_PATH` | Diff modifies files in `.github/workflows/`. | `422 Unprocessable Entity` (`GUARD_FAILED:G3_PROTECTED_PATH`) |
| `task-fx-deny-text` | `G4_DENY_TEXT` | Commit message contains forbidden token (`DENY_TEXT` or `WIP`). | `422 Unprocessable Entity` (`GUARD_FAILED:G4_DENY_TEXT`) |
| `task-fx-secret-pattern` | `G5_SECRET_DETECTED` | Diff contains detected token pattern (`ghp_` or `github_pat_`). | `422 Unprocessable Entity` (`GUARD_FAILED:G5_SECRET_DETECTED`) |
| `task-fx-disallowed-author` | `G6_AUTHOR_UNAUTHORIZED`| Commit author email not on allowed operator list. | `422 Unprocessable Entity` (`GUARD_FAILED:G6_AUTHOR_UNAUTHORIZED`) |
| `task-fx-drifted-head` | Head Race Check | Capability head SHA is set to parent commit while branch has moved ahead. | `409 Conflict` (`HEAD_DRIFT`) |
