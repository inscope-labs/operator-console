# Gateway A — Phase 2 Completion Report

- **Phase:** 2 — Core Enforcement Primitives
- **Date (UTC):** 2026-11-08
- **Author:** Gateway Stage Agent (session assignment)
- **Baseline SHA:** `b303c94` tip includes Stage 2.4; Phase 1 closed at `992deff` / Phase 1 report
- **Closing SHA(s):** `449257b` (2.1), `5fd80bb` (2.2), `fc47721` (2.3), `b303c94` (2.4), this report branch (2.5)

## 1. Stages closed

| Stage | Title | Closing SHA | CI run / evidence |
|-------|-------|-------------|-------------------|
| 2.1 | NonceStore completion & cleanup alarm | `0a18b2a` / merge `449257b` (PR #39) | [branch CI](https://github.com/inscope-labs/operator-console/actions/runs/42425952761) · [main](https://github.com/inscope-labs/operator-console/actions/runs/42426306171) |
| 2.2 | EnrolmentRegistry completion | `0eac843` / merge `5fd80bb` (PR #40) | [branch CI](https://github.com/inscope-labs/operator-console/actions/runs/42427574272) · [main](https://github.com/inscope-labs/operator-console/actions/runs/42427745356) |
| 2.3 | Remaining guards G5–G8, G11, G14, G15 | `ac9c3f4` / merge `fc47721` (PR #41) | [branch CI](https://github.com/inscope-labs/operator-console/actions/runs/42435158603) · [main](https://github.com/inscope-labs/operator-console/actions/runs/42435807980) |
| 2.4 | Capability verification edge cases | `fb5d0b4` / merge `b303c94` (PR #42) | [branch CI](https://github.com/inscope-labs/operator-console/actions/runs/42441940974) · [main](https://github.com/inscope-labs/operator-console/actions/runs/42445015691) |
| 2.5 | Phase 2 Completion Report | this commit | This document |

## 2. Exit criteria checklist

### Stage 2.1 — NonceStore
- [x] Concurrent identical capabilities → one success + one `NONCE_REPLAYED` — `nonce-store.test.ts`
- [x] Expired nonces removed by alarm; no unbounded accumulation — `alarm()` + purge tests
- [x] Tests green in CI

### Stage 2.2 — EnrolmentRegistry
- [x] Challenge reuse after success or expiry → `ENROLL_CHALLENGE_INVALID`
- [x] Rotate retires previous kid
- [x] Registry survives Worker restarts (shared DO storage map test)

### Stage 2.3 — Guards
- [x] `docs/guard-set.md` documents G1–G15 (no placeholder)
- [x] No guard remains a TODO / no-op
- [x] Tests prove each guard can independently refuse a request

### Stage 2.4 — Capability edge cases
- [x] Every capability-section catalogue code exercised by a test
- [x] Clock-skew tests use injectable `ctx.now`

### Stage 2.5
- [x] Phase Completion Report produced (Appendix A)
- [x] Guard inventory, DO test evidence, capability fail-closed confirmation (below)

## 3. Guard inventory (final status)

| ID | Name | Failure code | Implementation | Test evidence |
|----|------|--------------|----------------|---------------|
| G1 | Private repository | `REPO_NOT_PRIVATE` | `g1-private.ts` | `core.test.ts` |
| G3 | Branch shape | `BRANCH_INVALID` | `g3-branch-shape.ts` | `core.test.ts` |
| G4 | Branch exists | `BRANCH_MISSING` | `g4-branch-exists.ts` | `core.test.ts` |
| G5 | Ancestry | `ANCESTRY_FAIL` | `g5-ancestry.ts` | `remaining.test.ts` |
| G6 | Non-empty delta | `DELTA_EMPTY` | `g6-non-empty-delta.ts` | `remaining.test.ts` |
| G7 | No merge commits | `MERGE_COMMITS` | `g7-no-merge-commits.ts` | `remaining.test.ts` |
| G8 | Denied paths | `DENIED_PATH` | `g8-denied-paths.ts` | `remaining.test.ts` |
| G11 | Secret patterns | `SECRET_IN_DIFF` | `g11-secret-patterns.ts` | `remaining.test.ts` |
| G13 | Exact SHA | `HEAD_MOVED` | `g13-exact-sha.ts` | `core.test.ts` / edge-cases |
| G14 | Halt marker | `HALT_ACTIVE` | `g14-halt.ts` | `remaining.test.ts` |
| G15 | Submission immutable | `SUBMISSION_IMMUTABLE` | `g15-submission-immutable.ts` | `remaining.test.ts` |

Pipeline order (publish): **G1 → G3 → G4 → G5 → G6 → G7 → G8 → G11 → G13 → G14 → G15**  
Closing implementation SHA for full set: `ac9c3f4` (merged `fc47721`).

## 4. NonceStore & EnrolmentRegistry test evidence

### NonceStore (`0a18b2a` / PR #39)
- Transactional `check-and-store` (one 200 + one 409 under concurrent identical nonces)
- `alarm()` purges expired records; stats report live/expired
- Already-expired nonces refused without write
- Evidence: `gateway/tests/unit/durable-objects/nonce-store.test.ts` (8 tests)

### EnrolmentRegistry (`0eac843` / PR #40)
- Challenge consume-once; reuse/expiry → `ENROLL_CHALLENGE_INVALID`
- Concurrent consume → one success + one invalid
- Rotate retires old kid, activates new; failed rotate leaves old active
- Restart survival: new DO instance + same storage map retains enrolments and kid sequence
- Evidence: `gateway/tests/unit/durable-objects/enrolment-registry.test.ts` (9 tests)

## 5. Capability verification — no success on invalid input

Confirmed by Stage 2.4 (`fb5d0b4` / PR #42) and suite `edge-cases.test.ts`:

| Invalid condition | Code | Success path? |
|-------------------|------|---------------|
| Unknown kid | `UNKNOWN_KID` | No |
| Bad signature | `BAD_SIGNATURE` | No |
| TTL too long | `TTL_TOO_LONG` | No |
| Expired (beyond skew) | `CAPABILITY_EXPIRED` | No |
| Unknown op | `UNKNOWN_OP` | No |
| Branch mismatch | `BRANCH_MISMATCH` | No |
| Invalid branch pattern | `BRANCH_INVALID` | No |
| Wrong field count | `BAD_FIELD_COUNT` | No |
| Bad encoding (CR / framing) | `BAD_ENCODING` | No |
| Model A HMAC without `allowModelA` | `BAD_SIGNATURE` | No |

Model B is the production default; HMAC (Model A) is opt-in only via `allowModelA: true`.

## 6. Residual risks / deferred items

**None for Phase 2 scope.**

Deferred to later phases (not Phase 2 residuals):
- Real publish / upload / reconcile / halt / resume transactions → Phase 3
- Live staging deployment and V1–V12 → Phase 6
- Parent contract doc still partially uses alias error names; Gateway A catalogue remains authority

## 7. Security & hygiene

- [x] No secrets in source or report
- [x] `verify-log-hygiene` green (Gateway CI on Phase 2 merges)
- [x] `check-repo-privacy` green (Gateway CI on Phase 2 merges)

## 8. Operator sign-off

- **Decision:** PASS
- **Signed by:** John Wendell 
- **Date:** 2026-11-08
- **Notes:** Reviewed Stages 2.1–2.4 evidence; Gateway CI green on merge SHA 630ffd52c3bab17b9fdcee0ae9325643dc2e7b1f. Phase 3 authorized.