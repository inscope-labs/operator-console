# Error catalogue (frozen — Stage 1.3)

Stable public error codes returned by Gateway A as JSON:

```json
{ "error": "<ErrorCode>", "message": "<human summary>", "request_id": "<uuid>" }
```

- **Authority for runtime codes:** `gateway/src/errors/catalogue.ts`
- **HTTP status map:** identical to this document
- **Adding a code** is backwards-compatible; **changing meaning** requires an ADR
- Messages **never** include secrets, keys, challenges, signatures, or nonces

> **Contract note:** Parent `docs/gateway-contract.md` §4 historically used a body
> shape `{ "code", "message" }` and a partially different name set. Gateway A
> freezes on `{ "error", "message", "request_id" }` and the names below. Where the
> parent document used an alias, the mapping is listed in the “Aliases” section.

---

## Transport / credential

| Code | HTTP | Meaning |
|------|------|---------|
| `OPERATOR_AUTH` | 401 | Bearer `OPERATOR_TOKEN` required or invalid |
| `CREDENTIAL_NOT_ACCEPTED` | 400 | Presented credential is not accepted |
| `DEVICE_NOT_ENROLLED` | 401 | Device key is not enrolled or has been revoked |
| `TOKEN_UNAVAILABLE` | 503 | Required platform token is unavailable |

## Capability

| Code | HTTP | Meaning |
|------|------|---------|
| `UNKNOWN_KID` | 401 | Key selector is not recognized in the device registry |
| `BAD_SIGNATURE` | 401 | Capability signature verification failed |
| `TTL_TOO_LONG` | 400 | Capability TTL exceeds the maximum of 300 seconds |
| `UNKNOWN_OP` | 400 | Capability operation is not a member of the closed enumeration |
| `BRANCH_MISMATCH` | 403 | Capability branch does not match the request target |
| `HEAD_MOVED` | 409 | Expected head SHA does not match the remote branch HEAD |
| `NONCE_REPLAYED` | 409 | Capability nonce has already been presented |
| `CAPABILITY_EXPIRED` | 401 | Capability has expired |
| `BAD_FIELD_COUNT` | 400 | Capability wire message does not contain the required field count |
| `BAD_ENCODING` | 400 | Capability wire message has invalid encoding or framing |

## Guard layer

| Code | HTTP | Meaning |
|------|------|---------|
| `REPO_NOT_PRIVATE` | 403 | Target repository is not private |
| `BRANCH_INVALID` | 400 | Branch name does not match the required pattern |
| `BRANCH_MISSING` | 404 | Branch does not exist on the remote |
| `ANCESTRY_FAIL` | 409 | Branch ancestry check failed |
| `DELTA_EMPTY` | 400 | Branch delta is empty |
| `MERGE_COMMITS` | 400 | Branch contains merge commits |
| `DENIED_PATH` | 403 | Diff touches a denied path |
| `SECRET_IN_DIFF` | 403 | Diff contains a detected secret pattern |
| `HALT_ACTIVE` | 409 | Global engine halt is active; write operations are blocked |
| `SUBMISSION_IMMUTABLE` | 409 | Submission is immutable after acceptance |

## Adapter / platform

| Code | HTTP | Meaning |
|------|------|---------|
| `UNKNOWN_REPO` | 400 | Repository is not configured for this gateway |
| `RATE_BUDGET_LOW` | 429 | Adapter rate budget is exhausted |
| `RULESET_REJECTED` | 403 | Repository ruleset rejected the operation |
| `RECONCILE_REQUIRED` | 409 | Reconcile is required before this operation |
| `NETWORK` | 502 | Upstream or network error |

## Config / environment

| Code | HTTP | Meaning |
|------|------|---------|
| `CONFIG_INVALID` | 503 | Gateway configuration is invalid or incomplete; all routes except `/health` refuse work |
| `ENVIRONMENT_MISMATCH` | 403 | Client environment does not match gateway environment |

## Enrolment / attestation

| Code | HTTP | Meaning |
|------|------|---------|
| `ENROLL_CHALLENGE_INVALID` | 400 | Enrolment challenge unknown, expired, or already used |
| `ENROLL_ATTESTATION_INVALID` | 400 | Attestation chain, challenge binding, key, or tier mismatch |
| `AUTH_TIER_NOT_ALLOWED` | 403 | Derived auth tier is not permitted in this environment |

## Fail-closed stub

| Code | HTTP | Meaning |
|------|------|---------|
| `NOT_IMPLEMENTED` | 501 | Operation is in the contract but not implemented in this build. The request was refused before any work was performed. Never returned with a 2xx status. See ADR 0002. |

---

## Aliases (parent contract → frozen catalogue)

| Parent contract name | Frozen catalogue code |
|----------------------|------------------------|
| `NOT_ENROLLED` | `DEVICE_NOT_ENROLLED` |
| `EXPIRED` | `CAPABILITY_EXPIRED` |
| `HEAD_DRIFT` | `HEAD_MOVED` |
| `BRANCH_NOT_TASK` | `BRANCH_INVALID` |
| `GUARD_FAILED:*` | Specific guard codes above (`DENIED_PATH`, `SECRET_IN_DIFF`, …) |

---

## Freeze rule

After Stage 1.3, every new public error code must be added to both
`gateway/src/errors/catalogue.ts` and this document in the same change, with an
ADR note if meaning of an existing code is altered. Conformance vectors and
client `ErrorMapper` must reference the frozen names only.
