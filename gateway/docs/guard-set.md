# Guard set (G1–G15) — Stage 2.3 freeze

Authoritative ordered enforcement for privileged write paths on Gateway A.
Pipeline order (publish): **G1 → G3 → G4 → G5 → G6 → G7 → G8 → G11 → G13 → G14 → G15**.

G2 is reserved / unused in this build (legacy cbridge numbering gap).
G9, G10, G12, G16 are not part of the 0.6.0 authoritative set.

Each guard fails closed when required evidence is missing unless the guard is
explicitly scoped as conditional (G14 when halt is inactive; G15 when no
submission id is present).

| ID | Name | Inputs | Failure code | HTTP | Status |
|----|------|--------|--------------|------|--------|
| G1 | Private repository | `consumerIsPrivate` or `consumer.getRepoMetadata()` | `REPO_NOT_PRIVATE` | 403 | Implemented |
| G3 | Branch shape | `capability.branch` must match `^task-[A-Za-z0-9._-]+$` | `BRANCH_INVALID` | 400 | Implemented |
| G4 | Branch exists | `branchMeta.exists` or live ref lookup | `BRANCH_MISSING` | 404 | Implemented |
| G5 | Ancestry of base | `branchMeta.isAncestryOfMain` | `ANCESTRY_FAIL` | 409 | Implemented |
| G6 | Non-empty delta | `branchMeta.deltaEmpty` | `DELTA_EMPTY` | 400 | Implemented |
| G7 | No merge commits | `branchMeta.hasMergeCommits` | `MERGE_COMMITS` | 400 | Implemented |
| G8 | Denied paths | `branchMeta.changedPaths` must not touch `engine/`, `plans/`, `.github/` | `DENIED_PATH` | 403 | Implemented |
| G11 | Secret patterns | `diffText` / path list scanned for `ghp_`, `github_pat_`, PEM keys | `SECRET_IN_DIFF` | 403 | Implemented |
| G13 | Exact SHA | `capability.head` must equal live branch tip | `HEAD_MOVED` | 409 | Implemented |
| G14 | Halt marker | `haltActive === true` refuses writes | `HALT_ACTIVE` | 409 | Implemented |
| G15 | Submission immutable | `submissionId` + `submissionExists` (upload) | `SUBMISSION_IMMUTABLE` | 409 | Implemented |

## Audit fields (minimum)

On refusal, routes must emit audit with at least: `guard`, `code`, `request_id`,
`branch`, `head` (capability), and never secrets/nonces/keys.

## Wiring

- Modules under `gateway/src/guards/g*.ts` call `registerPublishGuard` at load time.
- `gateway/src/guards/register-core.ts` imports every module in pipeline order.
- `runGuards(ctx)` returns the first failure or `{ ok: true }`.
- Empty guard set fails closed with `NETWORK`.
