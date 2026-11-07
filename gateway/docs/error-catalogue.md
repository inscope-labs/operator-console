# Error Catalogue

**Status:** Frozen (Stage 1.3, ADR 0003)  
**Wire Format:** JSON `{ "code": "<MACHINE_READABLE_CODE>", "message": "<HUMAN_READABLE_SUMMARY>", "request_id": "<UUID>" }`  
*(Note: An `"error"` field is mirrored in JSON payloads for backwards compatibility with legacy client assertions).*

Adding a new error code is backwards-compatible; changing the meaning or status code of an existing code requires an ADR. Error messages MUST NEVER expose secrets, private keys, raw attestation tokens, challenges, signatures, or nonces.

---

## 1. Transport & Credential

| Code | HTTP | Meaning |
|---|---|---|
| `OPERATOR_AUTH` | 401 | Missing, malformed, or invalid `Authorization: Bearer <token>` header |
| `CREDENTIAL_NOT_ACCEPTED` | 400 | Operator credential format rejected |
| `DEVICE_NOT_ENROLLED` / `NOT_ENROLLED` | 401 / 403 | Device key selector `kid` is unknown, unverified, or revoked |
| `TOKEN_UNAVAILABLE` | 503 | Underlying platform/GitHub credential token is unavailable |
| `NETWORK` | 502 | Upstream platform or network communication failure |
| `NOT_FOUND` | 404 | Target route or repository resource does not exist |
| `RATE_BUDGET_LOW` | 429 | GitHub API rate limit budget exhausted |

---

## 2. Capability & Format Validation

| Code | HTTP | Meaning |
|---|---|---|
| `UNKNOWN_KID` | 401 | Key selector `kid` in capability is not recognized in device registry |
| `BAD_SIGNATURE` | 401 | ECDSA or HMAC cryptographic signature verification failed |
| `TTL_TOO_LONG` | 400 | Capability expiration timestamp exceeds gateway maximum TTL (300 s) |
| `CAPABILITY_EXPIRED` / `EXPIRED` | 401 / 400 | Capability expiry timestamp `exp` is in the past (clock skew tolerance: 10 s) |
| `UNKNOWN_OP` | 400 | Operation `op` is not a member of the closed operation set |
| `BRANCH_MISMATCH` | 403 | Target branch in request does not match minted capability branch |
| `BRANCH_NOT_TASK` / `BRANCH_INVALID` | 400 | Task-scoped branch name does not match `^task-[A-Za-z0-9._-]+$` or is `main` |
| `HEAD_MOVED` / `HEAD_DRIFT` | 409 | Remote branch tip SHA does not match expected head SHA in capability |
| `NONCE_REPLAYED` | 409 | Capability nonce has already been presented within the replay window |
| `BAD_FIELD_COUNT` | 400 | Wire message does not contain exactly 10 LF-separated fields |
| `BAD_ENCODING` | 400 | Wire message contains carriage returns (`\r`), leading/trailing LFs, or malformed base64url |

---

## 3. Guard Layer (G1–G16)

| Guard | Code | HTTP | Meaning |
|---|---|---|---|
| G1 | `REPO_NOT_PRIVATE` | 403 | Target consumer repository is not confirmed private |
| G3 | `BRANCH_INVALID` | 400 | Branch name does not conform to required task branch conventions |
| G4 | `BRANCH_MISSING` | 404 | Target task branch does not exist on remote |
| G5 | `ANCESTRY_FAIL` | 409 | Task branch is not a direct descendant of upstream base |
| G6 | `DELTA_EMPTY` | 400 | Task branch contains zero commits or empty tree delta against base |
| G7 | `MERGE_COMMITS` | 400 | Task branch diff contains merge commits |
| G8 | `DENIED_PATH` | 403 | Task branch touches protected path (`engine/`, `plans/`, `.github/workflows/`, etc.) |
| G11 | `SECRET_IN_DIFF` | 403 | Task branch diff contains detected secret patterns (tokens, private keys) |
| G13 | `HEAD_MOVED` | 409 | Remote branch HEAD does not match expected capability head |
| G14 | `HALT_ACTIVE` | 423 | Global repository halt marker is engaged; write actions locked |
| G15 | `SUBMISSION_IMMUTABLE`| 409 | Submission artifact identifier has already been recorded and cannot be overwritten |
| — | `GUARD_FAILED` | 422 | Generic guard failure when sub-code is formatted as prefix |

---

## 4. Adapter & Transaction Layer

| Code | HTTP | Meaning |
|---|---|---|
| `UNKNOWN_REPO` | 400 | Repository identifier is not registered in gateway configuration |
| `RULESET_REJECTED` | 403 | Upstream GitHub repository ruleset rejected compare-and-swap push |
| `RECONCILE_REQUIRED` | 409 | Task branch requires manual reconciliation before state transition |

---

## 5. Configuration & Environment

| Code | HTTP | Meaning |
|---|---|---|
| `CONFIG_INVALID` | 503 | Gateway environment variables, secrets, or repo maps failed validation |
| `ENVIRONMENT_MISMATCH` | 403 | Operation executed against disallowed environment (e.g., Lab outside `test`) |

---

## 6. Enrolment & Key Attestation

| Code | HTTP | Meaning |
|---|---|---|
| `ENROLL_CHALLENGE_INVALID` | 400 | Enrolment challenge is unknown, expired (TTL 300 s), or already consumed |
| `ENROLL_ATTESTATION_INVALID` | 400 | Certificate chain invalid, challenge mismatch, or public key binding mismatch |
| `AUTH_TIER_NOT_ALLOWED` | 403 | Derived attestation tier is not allowed in current environment policy |

---

## 7. Unimplemented Routes & Stubs

| Code | HTTP | Meaning |
|---|---|---|
| `NOT_IMPLEMENTED` | 501 | Operation is defined in contract but not yet implemented (fail closed) |
