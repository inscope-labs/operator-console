# Build Engine — Manual Pipeline Module Assessment (Revision 2)

**Repositories reviewed:** `inscope-labs/build-engine` and `inscope-labs/operator-console`
**Module:** `:manualpipeline` (host: Operator Console Android app)
**Purpose:** Assess the proposed Manual Pipeline against the Build Engine architecture and the Operator Console's non-negotiable invariants, answer its 18 outstanding questions, and identify the additions required for implementation.

---

## 0. Document control

| Item | Value |
|---|---|
| Revision | 2 (supersedes the uploaded corrected version) |
| Verified against | `operator-console` main `560882c`; `build-engine` main `9309a91` |
| Operator-console sources read | `docs/release/PHASE-3-REMEDIATION-REGISTER.md` §3 (I-01 to I-12); `docs/adr/0002`, `0008`, `0009`, `0013`, `0014`; `docs/operator-guide.md` §2–4; `docs/architecture.md`; `docs/gateway-contract.md` (§3.6–3.20); `docs/gateway-credential-scope.md`; `app/src/main/AndroidManifest.xml` |
| Build-engine sources read | `template/engine/tools/plan_tool.py`; `docs/release/TASK-STATE-TRANSITION-CONTRACT.md`; ADR 0011; ADR 0015 |
| Not independently verified | `gateway/write/docs/protocol.md` (commit mechanism, §3.4), ADR 0004 (lab mode), ADR 0012 text, the `template/engine/schemas/*` files, the `conformance/` suite contents |

### 0.1 Summary of changes from the uploaded version

| # | Change | Severity |
|---|---|---|
| C-1 | The console has **no** direct channel to Gateway B or Gateway C. Removed the Gateway B/C adapters and the "Repository adapter". All module traffic goes to Gateway A through the existing `data.remote` / `core.network` layer. | Critical |
| C-2 | Added the Operator Console invariants (I-01 to I-12, ADR 0002 credential rule, layering and permission rules) as a binding section (§1A) with a per-invariant mapping to the module and its tests. | Critical |
| C-3 | The console's upload operation is **archive-based** (`zipBase64` + `zipSha256`, contract §3.10). Per-file commit selection is a Gateway C (Class B) concept and is not available to the console. | High |
| C-4 | Sharpened Issue 1: `SET_DONE` requires a Gateway C seal for *every* task, but no documented route produces a seal for a console upload. This is a blocking contract gap, not only a divergence. | Critical |
| C-5 | Defined `PRE_TRANSFER_HANDOFF_READY` in terms the console can actually verify (authoritative status `done`, read back from Gateway A), because the console cannot read the seal fact itself. | High |
| C-6 | Journal: reuse the existing encrypted Room journal and shared executor (ADR 0009). No separate "journal adapter" and no separate idempotency namespace; each module action maps to exactly one existing contract operation. | High |
| C-7 | Recovery: aligned to the console's outcome vocabulary and to `GET /operations/{id}` lookup (ADR 0013). The app does not read repository heads or commits directly. | High |
| C-8 | Authorization: the console has no role system. Authority is the operator bearer, the enrolled device key, and a per-operation capability with a confirmation tier. | High |
| C-9 | Concurrency: no server-side lease exists in the published contract. Documented the guards that do exist and made the lease a contract addition requiring an ADR. | Medium |
| C-10 | Added the Phase 3 remediation dependencies (PR3-013, 020, 021, 026) that gate the module, including the missing gateway `POST /plans/{id}/status` route. | High |
| C-11 | Expanded architecture tests to cover every invariant; recorded the rejected "direct GitHub API client" approach. | Medium |

---

## 1. Repository findings

**Assessment:** Existing infrastructure is available; the Manual Pipeline itself is not implemented.

| Existing component | Relevance |
|---|---|
| `template/engine/tools/plan_tool.py` (build-engine) | Task eligibility, state transitions, validation and work-order generation |
| `template/engine/schemas/task.schema.json` | Canonical task status and task data model |
| `template/engine/schemas/order.schema.json` | Work-order structure and deliverable declarations |
| `template/engine/schemas/stages.schema.json` | Plan phases and implementation stages |
| `template/engine/schemas/submission.schema.json` | Submission structure |
| `template/operator/cbridge` | Existing operator-side upload, publication and reconciliation (Termux) |
| `gateway/write/` (build-engine) | Class B submission, staging, commit, sealing and the Gateway C seal fact |
| `docs/release/TASK-STATE-TRANSITION-CONTRACT.md` | Division of task-state (Gateway A) and seal (Gateway C) ownership |
| `docs/decisions/0011-task-state-and-cbridge-scope.md` | Task-state ownership and `cbridge upload` retirement rule |
| `docs/decisions/0015-submissions-to-consumer-transfer.md` | Supervisor Agent submissions-to-consumer transfer |
| `conformance/` | Behavioural and security acceptance infrastructure |
| `docs/gateway-contract.md` (operator-console) | The only gateway contract the Android app implements (Gateway A, rev 1.2) |
| `domain.usecase.*`, `data.local` journal, `core.network` (operator-console) | The existing journal, executor, minting path and HTTP layer the module must reuse |

The repository is therefore not starting from scratch.

### 1.1 Repository boundary

The Build Engine repository is the engine, policy, gateway and operator-tooling source. It does not contain the Android operator-console application.

The host belongs to the separate `inscope-labs/operator-console` repository. Consequently:

- Build Engine supplies the lifecycle, work-order, submission, commit and seal contracts.
- Operator Console supplies the native operator interface, authorization and interaction layer, and consumes the **contracts, not the code** (ADR 0015 item 2).
- Manual Pipeline consumes existing contracts rather than reproducing their functionality.

`:manualpipeline` must not be implemented as a new gateway or as another independent repository-writing subsystem.

### 1.2 Existing task-state capability

`plan_tool.py` implements a sanctioned task-state transition mechanism. Its canonical states are `planned`, `in_progress`, `pr_open`, `done`, `dropped` and `superseded`. It validates transitions and records operator attribution, timestamps, evidence and history.

| From | To | `plan_tool.py` rule |
|---|---|---|
| `planned` | `in_progress` | Eligibility checks required |
| `in_progress` | `planned` | Reason required |
| `in_progress` | `pr_open` | `--commit` reference required |
| `in_progress` | `done` | Operator-executor tasks only; `--notes` evidence summary required |
| `pr_open` | `done` | Merge SHA and PR reference required |
| `pr_open` | `planned` | Reason required |

The Gateway A legal-transition table (`TASK-STATE-TRANSITION-CONTRACT.md` §6) is **different**: it defines `SET_IN_PROGRESS` (`planned` → `in_progress`), `SET_DONE` (`in_progress` or `pr_open` → `done`, seal required) and `SET` (any non-terminal status to any other status except `done` and except the current one). The contract itself records this table as new and awaiting operator confirmation (its §9.1). Manual Pipeline must follow the Gateway A table, because that is what the console can call.

The Manual Pipeline must not introduce an independent lifecycle state machine that can disagree with the task model.

### 1.3 Stages versus task states

The repository has two separate concepts:

- **Implementation stages:** defined by each plan's `stages.json`.
- **Task lifecycle states:** defined by `task.schema.json`.

A task's implementation stage is not a lifecycle status. Manual Pipeline advances a task through its permitted lifecycle transitions while displaying the implementation stage to which the task belongs. It must not invent a universal stage sequence that overrides individual plan definitions.

---

## 1A. Operator Console invariants that bind this module

These are the console's non-negotiable rules. They come from the Phase 3 remediation register (§3, I-01 to I-12), ADR 0002, ADR 0009, ADR 0013, `docs/operator-guide.md` and `docs/architecture.md`. Where this assessment and an invariant disagree, the invariant wins; a change to an invariant is an operator decision recorded in an ADR, not a module design choice.

### 1A.1 The cardinal rule

> **No operator gets full repository access: only the minted branch.** The Operator Console holds no GitHub credential of any kind (ADR 0002).

The device holds only a non-exportable device keypair and a gateway-scoped `OPERATOR_CREDENTIAL`, which grants no repository access. The gateway's `GITHUB_TOKEN` is never the console's. The two names must never be reused for each other.

### 1A.2 Invariants and their effect on Manual Pipeline

| ID | Invariant | Effect on `:manualpipeline` | Enforcement |
|---|---|---|---|
| I-01 | Human authority: a privileged action needs explicit operator confirmation; a loaded screen, device authentication alone or credential possession is not authorization | Every consequential transition has its own confirmation. No batch approval, pre-approval, queued advancement or background minting | Architecture test: no minting reachable from a worker or from a non-confirm path |
| I-02 | One minting path: only `ConfirmAndMintCapabilityUseCase` mints | The module never mints. It calls the existing use case through the shared executor | Architecture test on callers of the minting API |
| I-03 | Journal before send | Intent and a 32-hex idempotency key are committed before the first network dispatch of every write | Executor test; no write path outside the journal |
| I-04 | No secret persistence | The journal, the module's workflow records and the audit never store a signed capability, signature, private key, nonce or raw credential | Redactor test; schema review of module tables |
| I-05 | No blind retry | An uncertain write is never resent. Resolution is by `GET /operations/{id}`; if still unresolved, surface `RecoveryRequired` | Fault-matrix tests (PR3-015); recovery worker is lookup-only |
| I-06 | No false success | Success is shown only after gateway acknowledgement, operation lookup where needed, and live-state validation | Each transition requires authoritative read-back |
| I-07 | Scope integrity | Each operation uses the exact operation, environment, plan, task, order, branch and head the contract requires; a repository-wide halt or resume never reuses a task-scoped capability | Contract-vector tests per operation |
| I-08 | Fail closed | Missing configuration, stale head, authentication failure, pinning failure, key invalidation, malformed response, unknown operation state or contract mismatch never falls through to success. An unavailable seal fact is a refusal, never an assumption | Negative tests per failure class |
| I-09 | No alternative execution channel | No Git credentials, shell, `Runtime.exec`, `ProcessBuilder`, local Git, direct GitHub client, or second write path around the journal | Architecture tests (PR3-033) |
| I-10 | Contract fidelity | Only operations and shapes in the authoritative versioned gateway contract. A missing or contradictory contract is a release blocker, not permission to invent an endpoint | Contract-conformance vectors; every new route needs a contract revision first |
| I-11 | Environment isolation | Staging and production keys, credentials and configuration stay isolated; an enrolment on one environment cannot mint for another | Per-environment namespacing tests |
| I-12 | Auditability | Each terminal result keeps the operation identifier and audit reference, and distinguishes success, refusal, conflict, timeout, uncertain completion and recovery required | Outcome-model tests |

### 1A.3 Structural rules from the architecture documents

| Rule | Source | Effect on the module |
|---|---|---|
| `core.network` is the only code that speaks HTTP; `data.remote` is the only gateway surface | `docs/architecture.md` | No second HTTP client, no module-owned networking |
| `ui` may call `domain` and read `domain.model`; it must not reach `data` directly | `docs/architecture.md` | Manual Pipeline screens depend only on module use cases |
| The app requests exactly `INTERNET` and `USE_BIOMETRIC` | ADR 0002; verified in `AndroidManifest.xml` | File selection must use the system document picker. No storage permission may be added |
| No class, preference key or request field accepts a GitHub token | ADR 0002 | Rejects any "operator pastes a PAT" design |
| `util/Redactor` forbids capability, signature and nonce in logs | ADR 0002; `architecture.md` | Module logging and audit go through the Redactor |
| The operator session is in memory only; the 5-minute capability TTL bounds authority | `operator-guide.md` §2.1 | A multi-step workflow cannot rely on a persisted "approved" state; each action is a fresh decision |
| Confirmation tiers: `hardware_confirm` or `in_app_confirm` (type the first 7 hex characters of the target HEAD); the dialog always shows the full 40-character SHA, operation, plan, task and branch | ADR 0012; `operator-guide.md` §3 | Every transition's summary card carries those fields. Production may reject `in_app_confirm`, in which case the module degrades to read-only |
| Failure vocabulary: `NOT_CONFIGURED`, `UNREACHABLE`, `TLS_PINNING`, `AUTH`, `STALE`, `POLICY_REFUSED`; refusals show the gateway's code and message and are never replaced by a generic failure | `operator-guide.md` §4.3; contract §8 of the transition document | Module error mapping must reuse these categories |
| Biometrics are optional; a missing biometric must not stop execution | Operator decision, recorded in the console plan | The module relies on the tier policy, not on biometrics |

### 1A.4 Rejected approach: a direct GitHub API client

A module that wrote to the submissions repository directly through the GitHub REST or Git Data API with an operator-held token was considered and **rejected**:

- It violates ADR 0002 (no repository credential on the device) and I-09 (no alternative execution channel).
- It bypasses the gateway's enforcement point: capability verification, replay protection, halt (G14), denied paths (G8), submission immutability (G15) and the gateway audit.
- It duplicates the gateway's write path (ADR 0002, "Alternatives considered").

The module's write operations are limited to the existing signed-capability gateway operations.

---

## 2. Answers to the 18 outstanding questions

The answers distinguish decisions supported by repository policy, recommended implementation decisions, and matters requiring explicit approval.

### Group A — Build Engine policy questions

#### 1. Lifecycle stages and transitions

**Assessment: Partially resolved.**

The repository establishes the canonical task states and the Gateway A transition table (§1.2). It does not establish a separate lifecycle-stage sequence for Manual Pipeline.

The implementation should:

- Read the task's implementation stage from the plan.
- Read its current status from Gateway A (`GET /plans/{id}/status`).
- Request only transitions Gateway A defines: `SET_IN_PROGRESS`, `SET`, `SET_DONE`.
- Advance one permitted transition per operator action.
- Record the resulting audit reference and artifact references.

A stage transition must not be confused with a task-state transition. The module must not reimplement the transition table locally; it presents the actions the contract allows and lets Gateway A refuse the rest.

#### 2. Valid task predicates

**Assessment: Partially resolved.**

The existing engine provides several eligibility checks:

- The task exists in the selected plan.
- The task passes schema and plan validation.
- Task dependencies are resolved.
- The task satisfies its executor and ordering constraints.
- The task does not violate task-conflict rules.
- HALT is not active before a state-changing operation.

No single predicate called `manual_pipeline_eligible` exists. The module needs a consolidated **advisory** evaluator, with these additional conditions:

1. A valid work order exists and references the selected task (the order identifier is supplied by the gateway on the task read, ADR 0014 D1 and contract rev 1.2).
2. The task is not terminal.
3. The task is not halted or sealed.
4. The recorded branch, head and order still match live values.
5. The operator is enrolled, authenticated and permitted (tier allowed in this environment).

**The gateway's guards are authoritative; the evaluator is advisory.** The console must display why a task is ineligible, but it may never treat its own evaluation as permission. A stale or wrong local evaluation must not allow a write the gateway would refuse, and a local pass is not authorization (I-01, I-08).

An existing task being `in_progress` should not automatically make it ineligible. The module must distinguish an operator-owned task from a task being processed elsewhere; because the published contract has no ownership primitive, this cannot be settled locally (see Question 14).

#### 3. Halt and seal semantics

**Assessment: Resolved for halt; resolved for seal with a correction.**

There are two separate controls:

- **HALT** prevents sanctioned engine writes. `plan_tool.py` checks configured root and plan-level halt markers. In the console contract, halt is enforced by the gateway guard G14 and set by `POST /halt` and `POST /resume`.
- **Seal** prevents further modification of a completed submission session.

The seal fact is Gateway C's: `GET {write gateway}/admin/task-state?task=<task>&order=<order>` with the operator token (`TASK-STATE-TRANSITION-CONTRACT.md` §2). `seal_attested` is true only when the session is sealed, a seal and a commit are recorded, the seal is over that commit, and the commit belongs to both the task and the work order.

**Correction.** The earlier text implied the module reads this route. It does not. Only **Gateway A** reads the seal fact, and only while processing `SET_DONE` (transition contract §4). The console sees the seal only indirectly: a `SET_DONE` is accepted, or it is refused with `SEAL_NOT_ATTESTED` (409) or `SEAL_FACT_UNAVAILABLE` (503).

The module must therefore:

- Respect HALT through the gateway guard and display halt state it learns from Gateway A.
- Never attempt to reopen a sealed task.
- Treat `SEAL_FACT_UNAVAILABLE` as a refusal. It may be retried only later, with a fresh capability, because the refused nonce is spent.
- Never offer to skip the seal check, and never record `done` locally on a refusal (transition contract §8).

#### 4. Transport

**Assessment: Resolved by the console contract.**

The Build Engine has three gateway boundaries (A: operator authority and task state; B: Class B read; C: Class B write and seal), plus the Class A operator route `cbridge`. **The console implements only Gateway A** (`docs/gateway-contract.md`). Gateway A reads from Gateway C on the console's behalf.

| Operation | Interface the module may use |
|---|---|
| Read plans, tasks, work-order identifier, status | Gateway A: `GET /plans`, `/plans/{id}/status`, `/tasks/next` |
| Read live branch head | Gateway A: `GET /branches/{name}/head` |
| Authoritative task-state transition | Gateway A: `POST /plans/{id}/status` (`SET`, `SET_IN_PROGRESS`, `SET_DONE`) |
| Submit the task archive | Gateway A: `POST /tasks/{id}/upload` (`UPLOAD`) |
| Resolve an uncertain write | Gateway A: `GET /operations/{id}` |
| Retrieve audit evidence | Gateway A: `GET /audit`, `GET /audit/{ref}` |
| Seal attestation | Not a console call: Gateway A reads Gateway C internally |
| Class B agent submission | Not a console operation: the agent's path through Gateway C |
| Consumer repository transfer | Separate Supervisor Agent path (ADR 0015); not available to the console |

The module must not communicate with GitHub, Gateway B or Gateway C directly. Adding any of those would need a contract revision first (I-10), new certificate pins and credential custody decisions (I-11), and would conflict with ADR 0002.

Two items still need documentation before implementation: the exact Gateway A reads the module relies on, and the Gateway A → Gateway C credential (Question 7).

#### 5. Work-order deliverable schema

**Assessment: Partially resolved, with a correction to the delivery unit.**

The work-order schema specifies the task ID and title, branch, task directory, acceptance criteria, deliverables, companion files, verification information and context references. The task schema also contains deliverables and companions. This is the basis for dynamic validation.

The module must not hardcode file types or a fixed artifact format. It should:

1. Load the original work order.
2. Identify the selected task.
3. Extract the exact required deliverables and companions.
4. Validate supplied files against those declarations.
5. Display missing, unexpected or invalid files.
6. Calculate SHA-256 digests and file sizes.
7. Present the exact submission contents for operator approval.

**Correction (contract §3.10).** The console's `UPLOAD` operation sends **one archive**: `zipBase64` plus `zipSha256`, with `submissionId`, `branch` and `head`. The delivery unit is therefore a ZIP archive, and the per-file validation in steps 4 to 6 is a **pre-flight check** before the archive is built or accepted. Selecting individual files for a commit is Gateway C's Class B behaviour and is not available to the console. "Do not hardcode ZIP files" applies to the *validation* of the work order's declared deliverables, not to the console's transport shape, which the contract fixes (I-10).

Operator decision needed: should the module (a) build the archive from operator-selected files, or (b) accept a prepared ZIP and validate its contents? Option (b) is smaller and matches `cbridge upload`.

Limits: the existing submission validator has a configurable maximum file size and checks UTF-8 content and expected file paths. The module must respect the engine and gateway policies and must not invent its own. File selection uses the system document picker; no storage permission may be added.

#### 6. Operator authorization model

**Assessment: Resolved; no new role system.**

The console has no Android role-management layer. Authority is the combination of:

- the **operator bearer** (`OPERATOR_CREDENTIAL`), which authenticates to the gateway's administrative surface and grants no repository access;
- the **enrolled device key**, namespaced per environment;
- a **per-operation signed capability**, minted only through the single minting path, with a confirmation tier (`hardware_confirm` or `in_app_confirm`) and a 5-minute TTL bound to the exact branch and head.

Authorization therefore applies as follows:

| Action | Authority required |
|---|---|
| Open Manual Pipeline, select a task, review deliverables | Read-only; no capability |
| Upload, advance a status, halt or resume | Capability minted after explicit confirmation |
| Resume or resolve an uncertain operation | Lookup is read-only; any new write needs a fresh decision and capability |

Every consequential transition must be attributable. Attribution comes from the enrolled identity and the gateway audit record, never from operator-typed text.

#### 7. Credential model

**Assessment: Resolved by ADR 0002; one upstream point remains open.**

- The module holds **no** GitHub credential of any kind: no PAT, OAuth token, App secret or consumer-transfer token.
- The ADR 0015 operator-supplied, task-scoped transfer token is used by the Supervisor Agent and is **never** given to the console.
- The write gateway's credential is scoped to the submissions repository only and cannot reach the consumer repository.
- Gateway A authenticates to GitHub as a GitHub App installation, never as a PAT or broad OAuth token (`gateway-credential-scope.md`).
- Credentials, capabilities, signatures and nonces never appear in task journals, audit records, module workflow records or logs; the Redactor covers them.
- Use the existing `OperatorCredentialStore` (Keystore-backed). Do not add a second credential store.
- Keep the naming rule: `OPERATOR_CREDENTIAL` (console) and `GITHUB_TOKEN` (write gateway) are different things.

**Open point (transition contract §9.2).** To read the seal fact, Gateway A needs a credential for Gateway C's operator channel. Today the only such credential is Gateway C's `OPERATOR_TOKEN`, which also permits revoke, close, quota reset, audit reads and minting. A read-only credential for this one route is the least-privilege alternative; it has not been chosen by any ADR. This is an operator decision and does not affect the console's own credential model.

#### 8. Pre-cbridge handoff state

**Assessment: Architecture resolved; verification condition redefined.**

The handoff is not the consumer-repository transfer. ADR 0015 specifies that the Supervisor Agent performs the transfer on the operator's instruction with a task-scoped token; the transfer is a build-engine operator-side path, not a gateway operation; it writes a new branch (`transfer/<work-order-id>`), opens a pull request against the consumer's default branch, never merges, and makes no partial writes.

For Manual Pipeline, the terminal condition is `PRE_TRANSFER_HANDOFF_READY` (see §3.2). The module must not execute the Supervisor Agent transfer.

#### 9. Commit and audit conventions

**Assessment: Partially resolved.**

The module must not define a commit protocol. For the console's operations, the gateway performs the repository write and returns an `auditRef`. The module records that reference and the idempotency key against its own operator-action record.

Gateway C's records (task and work-order association, commit SHA, selected paths, per-file hashes, commit timestamp, session and seal information) are Class B records the console cannot read directly. The console's evidence is the Gateway A audit (`GET /audit/{ref}`), the operation record (`GET /operations/{id}`) and the live status read-back.

The module adds its own operator-action record, linked to those references. The commit-message format proposed in the overview is a design proposal pending confirmation, and the console does not author commit messages.

#### 10. Journal ownership

**Assessment: Resolved; reuse the existing journal.**

| Record | Owner |
|---|---|
| Plan/task status | Gateway A |
| Submission session, commit, seal | Gateway C |
| Existing plan-tool local history | `plan_tool.py` (Termux path) |
| Consumer-transfer audit | Supervisor Agent transfer process |
| Console operation intent, idempotency key, outcome | Existing encrypted Room journal (ADR 0009) |
| Manual Pipeline workflow context (selected task, deliverable manifest, operator decisions) | New module tables, metadata only |

- Reuse the existing journal and the shared executor (ADR 0009; register item PR3-020). Do not create a competing journal.
- Do not keep a copy of authoritative task state; reference it.
- Every write is journalled before send, with a client-generated 32-hex idempotency key reused for repeated taps on the same intent (ADR 0013).
- **Correction:** there is no separate idempotency namespace. Each Manual Pipeline action maps to exactly one existing contract operation (`UPLOAD`, `SET_IN_PROGRESS`, `SET`, `SET_DONE`), and each carries its own key. The module's workflow record is local metadata only and never reaches the gateway.
- Module tables must obey I-04: no capability, signature, nonce, private key or raw credential.

### Group B — Explicit design decisions

#### 11. One stage per action versus queued stages

**Recommendation: one transition per action.**

Each operator action advances exactly one permitted lifecycle transition, with its own confirmation and its own capability. This is also an invariant, not only a preference: the console has no batch approvals, automatic pre-approvals or background minting (I-01).

Reasons:

- Preserves operator control.
- Allows verification between transitions.
- Limits the impact of incorrect decisions.
- Simplifies recovery.
- Makes audit records unambiguous.

The capability's 5-minute TTL and its binding to one branch and one head make a queued sequence impossible in practice. The operator repeats the action to advance further. Do not implement queued or unattended advancement.

#### 12. Resume semantics after a crash

**Recommendation: look up before resuming; never resend.**

The module must never assume that an interrupted write failed (I-05).

Recovery sequence:

1. Load the outstanding operation journal entry.
2. Identify the last confirmed transition.
3. Query `GET /operations/{idempotencyKey}` for the recorded status.
4. Read the authoritative task status from Gateway A (`GET /plans/{id}/status`) and the live branch head (`GET /branches/{name}/head`).
5. Reconcile gateway state against the journal.
6. Present the resolved outcome to the operator.

`404 OPERATION_NOT_FOUND` maps to `RecoveryRequired`, never to an automatic resend (ADR 0013). The recovery worker performs lookups only: it holds no signing capability, never mints, and never writes.

The outcome vocabulary is the console's, not a shortened list:

- `Succeeded`
- `Refused`
- `Conflict`
- `Timeout`
- `UncertainCompletion`
- `RecoveryRequired`

Each terminal result keeps the operation identifier and the audit reference where supplied (I-12). An uncertain operation must never be blindly repeated. The app does not read repository heads or inspect commits itself; it asks the gateway.

#### 13. Conflict handling

**Recommendation: fail closed.**

The module must not resolve conflicts automatically. If the upstream branch has moved (`HEAD_MOVED` or `STALE`):

- Stop the operation.
- Preserve the existing journal entry.
- Display the expected and actual references.
- Explain which precondition failed, using the gateway's code and message.
- Require the operator to reassess the task.

A fresh operation, with a new idempotency key and a fresh capability, is created only after the operator has re-inspected. Reusing a key with a changed request is refused by the gateway as `IDEMPOTENCY_KEY_REUSED` (409). Do not automatically rebase, overwrite or discard deliverables.

#### 14. Multi-operator concurrency

**Assessment: partly covered; a server-side lease needs a contract addition.**

Guards that exist today:

- A capability is single-use (`NONCE_REPLAYED` on reuse) and bound to one branch and one head.
- Writes are compare-and-swap against the expected head (`HEAD_MOVED`).
- A repeated key with the same request returns the recorded response; a different request with the same key is refused.
- Gateway C has a single-writer claim for its own sessions, and `plan_tool.py` has plan-level file locking on the Termux path.

None of these is a cross-device ownership mechanism for Manual Pipeline. A task-level lease would need a new gateway route, which the console cannot add on its own (I-10); it requires a contract revision and an ADR, plus conformance cases.

Recommended lease properties, if approved:

- One active owner per task.
- Lease bound to the enrolled identity and the operation ID.
- Expiry or explicit release.
- Recovery after application termination.
- Server-side enforcement.

A local Android lock alone is insufficient and must not be presented as protection. Until a lease exists, the optimistic guards above are the real protection, and the UI must say so.

#### 15. UI entry point and navigation

**Recommendation: task-detail action and dedicated section.**

Two entry points:

1. A dedicated Manual Pipeline section for inspecting eligible tasks.
2. A Manual Pipeline action from an individual task's detail screen.

Both open the same module and the same operation state. Screens depend on module use cases only; they never reach `data` (architecture rule). The module is operator-exclusive and is not a worker-facing capability. Where the environment or device tier cannot confirm, the module is read-only.

#### 16. Attribution granularity

**Recommendation: transition-level attribution.**

Record attribution at three levels:

- Operator session (in memory only; the session is not persisted).
- Individual lifecycle decision.
- Individual consequential gateway write.

Each module record contains:

- Enrolled operator identity (from enrolment and the authenticated session, not user-typed text).
- Task and work-order IDs.
- Operation ID (idempotency key) and operation type.
- Confirmation tier used.
- Previous and resulting status, as read from Gateway A.
- Timestamp.
- Decision reason, where the operation requires one.
- Gateway `auditRef` and archive digest, where applicable.
- Outcome, from the console vocabulary.

No record may contain a capability, signature, nonce or credential. The authoritative audit remains the gateway's (`GET /audit/{ref}`); the module's record is linked to it, not a substitute for it.

#### 17. Architecture-test enforcement

**Assessment: required.**

The following must be enforced by automated tests. The first group covers the module's own scope; the second group maps the console invariants and the structural rules.

*Module scope*

1. Manual Pipeline cannot create or accept tasks.
2. Manual Pipeline cannot modify work orders.
3. Manual Pipeline cannot bypass task eligibility enforcement (the gateway's guards).
4. Manual Pipeline cannot bypass HALT.
5. Manual Pipeline cannot reopen sealed sessions, record `done` locally, or skip the seal check.
6. Manual Pipeline cannot perform consumer-repository transfer.
7. Manual Pipeline cannot publish or merge consumer pull requests.
8. Each operator action advances at most one transition.
9. Successful transitions require authoritative read-back.
10. Uncertain writes cannot be blindly repeated.

*Console invariants and structure*

11. Capability minting is reachable only through `ConfirmAndMintCapabilityUseCase` (I-02), and never from a worker (I-01).
12. Every write goes through the journal before dispatch (I-03); no write path bypasses the shared executor (I-09).
13. No capability, signature, nonce, private key or credential is persisted in the journal, module tables, audit records or logs (I-04, I-12); `Redactor` covers the forbidden set.
14. No class, preference key or request field accepts a GitHub token; the manifest requests exactly `INTERNET` and `USE_BIOMETRIC` (ADR 0002).
15. No GitHub, Gateway B or Gateway C client exists in the module; only `core.network` speaks HTTP (I-09, I-10).
16. No `Runtime.exec`, `ProcessBuilder`, local Git or shell use (I-09).
17. `ui` does not import `data`.
18. Per-operation requests match the published contract and its conformance vectors (I-07, I-10).
19. Environment-namespaced keys and configuration cannot be reused across environments (I-11).
20. Success is never reported without acknowledgement, lookup where needed, and live-state validation (I-06); unknown, malformed or mismatched states never fall through to success (I-08).
21. Lab-mode code cannot be imported by release code (register rule under PR3-033).

The tests live primarily in the Operator Console repository, with shared contract and conformance tests where Build Engine behaviour is involved. Many of these rules already have, or are the subject of, architecture tests (register items PR3-033 and PR3-020); the module extends that suite and does not create a parallel one.

#### 18. Provisional safety primitives

**Assessment: strongly supported by existing conventions.**

| Proposed primitive | Assessment |
|---|---|
| Journal before send | Adopt (I-03) |
| Per-operation idempotency | Adopt; 32-hex client key per ADR 0013, one per contract operation |
| Read-back verification | Adopt (I-06) |
| Fail-closed recovery | Adopt (I-05, I-08) |
| Local operator audit | Adopt, linked to authoritative records |
| Credential redaction | Mandatory (I-04) |
| Commit SHA tracking | Use the head the gateway reports; the console does not author commits |
| Per-file SHA-256 | Pre-flight in the console; the contract carries `zipSha256` for the archive |
| Single-writer protection | Extend existing mechanisms; server-side lease needs a contract addition |
| Atomic stage commits | Provided by the gateway's compare-and-swap, not by the console |

Local audit alone is not evidence of a successful upstream operation. The authoritative gateway records must be consulted.

---

## 3. Important architectural corrections to the overview

There are six areas to correct before approving the ADR.

### 3.1 Manual Pipeline is not a second implementation of `plan_tool.py`

The engine already has task-state validation and transition logic. The module invokes the Gateway A contract (`POST /plans/{id}/status`) rather than reimplementing a transition table. This avoids state divergence.

### 3.2 Manual Pipeline must distinguish submission from transfer

The terminal state is called:

`PRE_TRANSFER_HANDOFF_READY`

This is a proposed module-level state, **not** an addition to the six canonical task statuses. Because the console cannot read the seal fact itself, it is defined by what the console can verify:

1. The submission upload was accepted, with an `auditRef` and the archive digest recorded.
2. Gateway A reports the task status as `done`, read back after the transition. `done` is reachable only through `SET_DONE`, and Gateway A accepts `SET_DONE` only against a seal it has cross-checked with Gateway C for this task and this work order.
3. The operator has confirmed handoff, and the handoff references are available.

It means a verified and sealed submission awaiting the separate Supervisor Agent transfer. It is not a cbridge-ready state, and the module stops there.

### 3.3 The module must not depend on the 0.5.0 release artifact for Class B functionality

Build Engine 0.5.0 Basic is Class A only. The Class B gateways belong to the 0.6.0 Standard integration work. The presence of gateway code in `main` does not mean it is available in the published 0.5.0 artifact. The module must use the deployed and approved interfaces in the target environment.

### 3.4 The overview should not prescribe a new commit protocol

Gateway C has a defined commit mechanism for Class B submissions (validate selected files, check the expected branch head, create blobs and a tree, create a commit, record it before moving the branch, move the branch, read back and reconcile uncertain outcomes). *Not independently verified in this revision.*

That mechanism belongs to Class B agent submissions and is not a console operation. For the console, the equivalent guarantees come from Gateway A: guards G1 to G15, compare-and-swap against the expected head, the recorded operation, and the audit reference. The module consumes those semantics and prescribes nothing new.

### 3.5 The module must not become a general-purpose Git client

The module exposes only task-scoped lifecycle operations, never arbitrary branch, commit, push, merge or repository-management functionality. This is also a hard requirement of I-09 and ADR 0002.

### 3.6 The module must not extend the console's gateway footprint

The uploaded overview placed Gateway B and Gateway C adapters and a repository adapter inside the console. None of these is in the console's contract, none has a pinned host or credential model in the console, and a repository adapter contradicts ADR 0002. All module traffic goes to Gateway A (§2, Question 4). Adding any other endpoint requires a contract revision first (I-10) and an environment and pinning decision (I-11).

---

## 4. Required additions for immediate implementation

Implementation is divided into three work packages: the native module, the supporting build-engine and gateway work, and the contract decisions that block both.

### 4.1 Operator Console — new Manual Pipeline module

| ID | Addition | Purpose |
|---|---|---|
| MP-001 | Task discovery | Find work-order-backed tasks eligible for manual progression, using Gateway A reads |
| MP-002 | Advisory eligibility evaluator | Show task, dependency, halt and tier readiness; never grants permission |
| MP-003 | Lifecycle controller | Request one permitted Gateway A transition per operator action through the shared executor |
| MP-004 | Work-order interpreter | Extract required deliverables and companions from the work order |
| MP-005 | Deliverable manager | Pick files through the system document picker, inspect and validate them against the work order |
| MP-006 | Artifact integrity | Calculate SHA-256, sizes and the archive digest (`zipSha256`) from the exact bytes uploaded |
| MP-007 | Stage verification | Verify gateway-reported effects (status, head, audit reference) before permitting progression |
| MP-008 | Upload integration | Submit the archive through the existing `UPLOAD` use case and executor |
| MP-009 | Workflow records | Module metadata tables beside the existing journal (no secrets, I-04) |
| MP-010 | Recovery controller | Lookup-only reconciliation via `GET /operations/{id}`; no resend |
| MP-011 | Concurrency presentation | Surface head and nonce guard refusals; lease support only if the contract adds it |
| MP-012 | Authority integration | Reuse enrolment, operator bearer and confirmation tiers; no new role system |
| MP-013 | Audit manager | Operator-action records linked to the gateway `auditRef` |
| MP-014 | Handoff manager | Establish and verify `PRE_TRANSFER_HANDOFF_READY` (§3.2) |
| MP-015 | Lifecycle dashboard | Display status, completed work, artifacts and the next permitted action |

### 4.2 Required application interfaces

The module needs explicit use-case-level adapters over **existing** services only.

| Interface | Required functionality | Source |
|---|---|---|
| Task and plan read adapter | Plans, task, order identifier, status, live head | Existing Gateway A reads |
| Upload adapter | Archive submission through the executor | Existing `UPLOAD` operation |
| Status-transition adapter | `SET_IN_PROGRESS`, `SET`, `SET_DONE` through the executor | Existing `SetTaskStatusUseCase` (`POST /plans/{id}/status`) |
| Operation lookup adapter | Resolve uncertain writes | Existing `GET /operations/{id}` |
| Audit read adapter | Link local records to gateway evidence | Existing `GET /audit` |
| Authority adapter | Enrolment, operator bearer, confirmation tier | Existing console authority code |
| Journal adapter | Durable intent and outcome | Existing encrypted Room journal (ADR 0009) |

Removed from the earlier version: the Gateway B adapter, the Gateway C adapter and the repository adapter.

Do not introduce a second HTTP client, credential manager, journal or repository abstraction.

### 4.3 Build Engine and gateway additions

| ID | Addition | Required work |
|---|---|---|
| BE-MP-01 | Manual Pipeline integration contract | Document supported lifecycle operations and preconditions |
| BE-MP-02 | Eligibility contract | Define the complete valid-task predicate and which parts the gateway enforces |
| BE-MP-03 | Lifecycle transition contract | Reconcile `plan_tool.py` transitions with Gateway A's legal-transition table, and resolve Issue 1 |
| BE-MP-04 | Operation identity | Define operation IDs, idempotency and journal correlation (builds on ADR 0013) |
| BE-MP-05 | Artifact verification contract | Standardise archive manifest, digest and audit-reference semantics |
| BE-MP-06 | Recovery contract | Define outcomes and read-back requirements for each operation |
| BE-MP-07 | Concurrency contract | Define task ownership and lease behaviour, if approved |
| BE-MP-08 | Handoff contract | Define the evidence required before Supervisor Agent transfer |
| BE-MP-09 | Conformance suite | Test lifecycle, recovery, authorization and boundary invariants |
| BE-MP-10 | Architecture documentation | Document the module's position in the overall system |
| BE-MP-11 | Seal-fact access for the console | Decide whether Gateway A exposes a console-readable seal indicator, or `done` is the sole evidence (§3.2) |
| BE-MP-12 | Console-upload sealing | Define how an upload through Gateway A produces, or is exempted from, the Gateway C seal (Issue 1) |

These are additions to existing infrastructure, not replacements.

### 4.4 Required testing

The initial implementation should include:

1. Valid task discovery.
2. Invalid or unaccepted work-order rejection.
3. HALT enforcement (gateway refusal displayed with its code).
4. Sealed-task rejection, including `SEAL_NOT_ATTESTED` and `SEAL_FACT_UNAVAILABLE`.
5. Missing deliverable rejection.
6. Unexpected deliverable rejection.
7. Digest mismatch detection (the archive hash is computed from the uploaded bytes).
8. Successful single-transition progression with read-back.
9. Illegal transition rejection (`TRANSITION_NOT_ALLOWED`, including `SET` to `done`).
10. Upstream branch movement (`HEAD_MOVED`).
11. Write success with a lost response (`UncertainCompletion`, then lookup).
12. Application termination during a write.
13. Application restart and recovery, with no resend.
14. Concurrent operator access (nonce replay, head move, key reuse).
15. Credential and authorization boundary enforcement, including the absence of any GitHub credential path.
16. Audit integrity and the forbidden-field set.
17. Prevention of consumer-repository transfer.
18. Successful `PRE_TRANSFER_HANDOFF_READY` verification.
19. `in_app_confirm` rejected in an environment that disallows it, degrading to read-only.
20. Environment isolation: a staging enrolment cannot be used for production.

The most important tests prove that a failed or uncertain operation cannot silently advance the lifecycle.

---

## 5. Critical issues requiring resolution before implementation

### Issue 1 — Task-state divergence, and no route to a seal for console uploads (blocking)

`plan_tool.py` permits `in_progress` → `done` for operator-executor tasks when `--notes` are supplied. The authoritative transition contract requires `SET_DONE` to be backed by a Gateway C seal attestation for the task and work order, and refuses `SET` to `done`.

The console's upload goes through Gateway A (`UPLOAD`). I found no documented route by which that upload creates a Gateway C session, commit or seal. If none exists, a task completed through Manual Pipeline cannot reach `done` through the console: `SET_DONE` would be refused with `SEAL_NOT_ATTESTED`.

This must be resolved by the operator and the build-engine owner before MP-3, in one of three ways:

1. Define how a Gateway A upload produces a Gateway C seal (BE-MP-12).
2. Define an operator-executor exception to the seal rule, if the operator approves weakening it (this would change ADR 0011 item 1).
3. Restrict Manual Pipeline to transitions that need no seal (`SET_IN_PROGRESS`, `SET`) and leave `done` to the Class B path.

Until then, the module must not call `SET_DONE` and must never record `done` locally.

Sources: `plan_tool.py` lines for the transition table and operator `done`; `TASK-STATE-TRANSITION-CONTRACT.md` §4, §6 and §9; ADR 0011.

### Issue 2 — Incorrect cbridge transfer terminology

ADR 0015 establishes that the sealed submissions-to-consumer transfer is performed by the Supervisor Agent, not by a final cbridge operation. `cbridge upload` remains supported for Class A delivery during 0.6.0 and is retired only after the console's upload passes OC-8. Manual Pipeline terminates before the Supervisor Agent transfer.

### Issue 3 — Gateway A and Gateway C remain separate authorities

Gateway A owns task status. Gateway C owns submission state and the seal. Gateway A never infers a seal and Gateway C never records a status. A successful submission is not permission to change task status; both authorities act independently, and the console talks only to Gateway A.

### Issue 4 — The console boundary was wrong in the earlier version

The earlier text placed Gateway B, Gateway C and repository adapters inside the app. These contradict ADR 0002, I-09, I-10 and I-11. See §3.6. Any future need for them requires an ADR.

### Issue 5 — Phase 3 remediation dependencies

The module depends on console work that is still in progress under the Phase 3 remediation register:

| Dependency | Why the module needs it | State when verified |
|---|---|---|
| PR3-021 (order identifier) | The module needs the work-order identifier on the task read (ADR 0014 D1, contract rev 1.2) | Android `Task` model lacks `order`; open |
| PR3-026 / ADR 0014 D3 (status route) | `POST /plans/{id}/status` is the only transition route | Not implemented in the gateway on `main` (only `GET /plans/{id}/status` is registered); open |
| PR3-020 (shared executor) | Every module write must use the shared executor | In progress |
| PR3-013 (acknowledgement and live-head consistency) | Success requires read-back | Landed on `main`; not accepted |
| PR3-015 (fault matrix) | Recovery evidence | Open |
| PR3-043 (instrumented CI) and device evidence | Android-runtime evidence for recovery and tiers | Open |

Module work that needs a route the gateway does not yet implement is blocked until the route exists (I-10).

---

## 6. Recommended implementation sequence

### MP-0 — Contract reconciliation

Resolve Issue 1, confirm the eligibility predicate (advisory versus gateway-enforced), settle the archive-building question (Question 5), document the Gateway A reads and routes the module relies on, decide the lease question (Question 14), and write the ADR that approves the module and records the rejected direct-GitHub approach (§1A.4).

### MP-1 — Module foundation

Task discovery, work-order interpretation, the advisory evaluator and the lifecycle dashboard, all read-only. Gate: PR3-021.

### MP-2 — Deliverable and upload management

Dynamic deliverable validation, digest generation, and integration with the existing `UPLOAD` use case and executor. Gate: PR3-020.

### MP-3 — Lifecycle progression

One-transition-per-action execution through `SetTaskStatusUseCase`, operator confirmation, attribution and authoritative read-back. Gate: gateway `POST /plans/{id}/status` (PR3-026) and the Issue 1 decision.

### MP-4 — Recovery and concurrency

Journalled intent, idempotency, lookup-only recovery, conflict reporting, and a lease only if MP-0 approves one.

### MP-5 — Handoff and conformance

`PRE_TRANSFER_HANDOFF_READY` verification, architecture and boundary tests (§2, Question 17), security tests and end-to-end acceptance.

---

## 7. Overall assessment

**Assessment: suitable for implementation after MP-0.**

The proposed Manual Pipeline is architecturally compatible with the existing Build Engine and Operator Console, once it is confined to the console's invariants: Gateway A only, no repository credential, a single minting path, journal before send, no blind retry, and no false success.

Most foundational requirements already exist:

- Task and work-order schemas.
- Task eligibility and transition logic.
- Operator-side submission handling (`cbridge upload`, and the console's `UPLOAD`).
- Gateway-based submission and sealing.
- Idempotency, lookup and recovery (ADR 0013).
- The encrypted journal, shared executor and single minting path.
- Task-state ownership and the consumer-transfer architecture.

The missing pieces are the operator-facing lifecycle controller, its module records and integration contracts, and the contract decisions in Issue 1 and Question 14.

**Principal recommendation:** do not build another pipeline underneath the existing pipeline. Build Manual Pipeline as an operator-controlled orchestration layer over Gateway A, reusing the console's existing use cases, journal and executor. The operator makes every consequential progression decision; the module handles validation, execution mechanics, evidence collection and recovery.

---

## 8. Decisions needed from the operator

| # | Decision | Needed before |
|---|---|---|
| 1 | Issue 1: how a console submission reaches a seal, or whether an operator-executor exception to `SET_DONE` is allowed | MP-3 |
| 2 | Question 5: build the archive from selected files, or accept a prepared ZIP | MP-2 |
| 3 | Question 14: approve a server-side task lease, or rely on the existing optimistic guards | MP-4 |
| 4 | Question 7: least-privilege read-only credential for Gateway A to read Gateway C's seal fact | Staging pilot |
| 5 | Transition table in `TASK-STATE-TRANSITION-CONTRACT.md` §6 (open point 1) | MP-3 |
| 6 | Whether `done` alone is sufficient handoff evidence, or Gateway A should expose a seal indicator (BE-MP-11) | MP-5 |
| 7 | Approve the module through an ADR, recording the rejected direct-GitHub approach | MP-0 |

---

**Recommended next step:** produce the MP-0 ADR and the numbered, file-by-file implementation specification for `operator-console` and `build-engine`, keeping existing infrastructure separate from genuinely new code. Do not start MP-3 until decisions 1 and 5 are made and the gateway status route exists.
