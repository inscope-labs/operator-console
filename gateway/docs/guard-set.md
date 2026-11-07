# Guard Set Specification (G1–G16)

**Status:** Authoritative (BD-1.5, Stage 1.3)  
**Component:** `gateway/src/guards/`

Every mutating operation (e.g. `publish`, `upload`, `reconcile`) evaluates an ordered pipeline of fail-closed guards before performing compare-and-swap mutations or issuing upstream GitHub commits. Guards receive a typed `GuardContext` and return either `{ ok: true }` or `{ ok: false, code: ErrorCode, message: string }`. Any rejection halts execution immediately; no nonce is stored, no commit is written, and no audit record is written to the consumer repository.

---

## 1. Execution Order & Pipeline

For publish-grade mutating transactions, guards are executed in the following strict order:

```
G1 (Private Repo) ──▶ G3 (Branch Shape) ──▶ G4 (Branch Exists) ──▶ G13 (Exact SHA)
        │
        ├──▶ G5 (Ancestry) ──▶ G6 (Non-Empty Delta) ──▶ G7 (No Merge Commits)
        │
        ├──▶ G8 (Denied Paths) ──▶ G11 (Secret Patterns) ──▶ G14 (Halt Marker)
        │
        └──▶ G15 (Submission Immutable) ──▶ [CAS Transaction & Audit]
```

*(Note: G16 is the explicit operator confirmation gesture and biometric prompt, executed client-side on the Operator Console prior to capability minting).*

---

## 2. Guard Registry

### G1 — Private Repository Assertion
- **Purpose**: Enforces that the consumer repository is private. Never publish to or read from a public repository without explicit configuration.
- **Trigger**: Checked on gateway startup and on every mutation context.
- **Failure Code**: `REPO_NOT_PRIVATE` (HTTP 403).
- **Audit Field**: `guard_g1_private: true`.

### G2 — Reserved / Legacy Alias
- **Purpose**: Retained for backward numbering compatibility with legacy CLI specifications.

### G3 — Branch Shape & Name Validation
- **Purpose**: Ensures the target branch matches `^task-[A-Za-z0-9._-]+$` for task-scoped operations and is never `main` or an engine branch.
- **Trigger**: Capability and request body validation.
- **Failure Code**: `BRANCH_INVALID` (HTTP 400).
- **Audit Field**: `guard_g3_branch_shape: true`.

### G4 — Branch Remote Existence
- **Purpose**: Verifies that the branch exists on remote GitHub upstream.
- **Trigger**: Pre-flight ref lookup.
- **Failure Code**: `BRANCH_MISSING` (HTTP 404).
- **Audit Field**: `guard_g4_branch_exists: true`.

### G5 — Ancestry Check
- **Purpose**: Guarantees that the task branch is a direct, linear descendant of the configured base branch (`main`).
- **Trigger**: Commit graph traversal.
- **Failure Code**: `ANCESTRY_FAIL` (HTTP 409).
- **Audit Field**: `guard_g5_ancestry: true`.

### G6 — Non-Empty Delta
- **Purpose**: Rejects branches that contain zero net changes or identical tree SHAs compared to base.
- **Trigger**: Diff calculation between base and task HEAD.
- **Failure Code**: `DELTA_EMPTY` (HTTP 400).
- **Audit Field**: `guard_g6_delta_bytes: <size>`.

### G7 — No Merge Commits
- **Purpose**: Enforces a linear commit history. Branches containing merge commits (`parents.length > 1`) are rejected.
- **Trigger**: Commit history scan of task branch commits.
- **Failure Code**: `MERGE_COMMITS` (HTTP 400).
- **Audit Field**: `guard_g7_no_merges: true`.

### G8 — Denied Paths
- **Purpose**: Prevents modifications to protected internal directories:
  - `engine/**`
  - `plans/**`
  - `.github/workflows/**`
  - `gateway/**`
- **Trigger**: File path inspection in branch diff.
- **Failure Code**: `DENIED_PATH` (HTTP 403).
- **Audit Field**: `guard_g8_paths_verified: true`.

### G9 / G10 — Reserved / Task Metadata Bounds
- **Purpose**: Reserved for structured plan boundary checks.

### G11 — Secret Patterns in Diff
- **Purpose**: Scans all modified lines in the unified diff for credential and token patterns (e.g., `ghp_`, `github_pat_`, `AKIA`, private key headers `BEGIN PRIVATE KEY`).
- **Trigger**: Regex matching over diff patches.
- **Failure Code**: `SECRET_IN_DIFF` (HTTP 403).
- **Audit Field**: `guard_g11_clean_diff: true`.

### G12 — Reserved / Rate & Quota Budget
- **Purpose**: Reserved for operator execution quota enforcement.

### G13 — Exact SHA Head Match
- **Purpose**: Concurrency protection against race conditions and head drift. The live branch tip SHA must match the `head` SHA specified in the capability.
- **Trigger**: Direct equality check `liveSha === capability.head`.
- **Failure Code**: `HEAD_MOVED` (HTTP 409).
- **Audit Field**: `guard_g13_head_sha: <sha>`.

### G14 — Global Halt Marker
- **Purpose**: Emergency kill switch. When the repository or engine halt marker is active in Durable Object storage, all mutating operations are blocked.
- **Trigger**: Durable Object state check.
- **Failure Code**: `HALT_ACTIVE` (HTTP 423).
- **Audit Field**: `guard_g14_halt_status: "active"|"inactive"`.

### G15 — Submission Immutability
- **Purpose**: Replay and overwrite protection for task submissions. A submission identifier once recorded in the audit trail cannot be modified.
- **Trigger**: Submission registry lookup.
- **Failure Code**: `SUBMISSION_IMMUTABLE` (HTTP 409).
- **Audit Field**: `guard_g15_submission_id: <id>`.

### G16 — Explicit Operator Confirmation
- **Purpose**: Cryptographic proof of human confirmation. Minted via hardware key attestation or biometric prompt on the Operator Console.
- **Trigger**: Verified by Capability verification gate prior to running G1–G15.
- **Failure Code**: `UNKNOWN_KID` / `BAD_SIGNATURE` / `AUTH_TIER_NOT_ALLOWED`.
- **Audit Field**: `kid`, `authTier`.
