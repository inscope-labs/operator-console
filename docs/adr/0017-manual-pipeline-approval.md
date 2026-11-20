# ADR 0017 — Manual Pipeline approval

| | |
|---|---|
| **Status** | Accepted in part (takes effect when this PR is merged); three points remain open |
| **Date** | 2026-11-18 |
| **Decision IDs** | Manual pipeline Decisions 1 to 5 and 7 (assessment section 8); Decision 6 open |
| **Related** | `docs/release/MANUAL-PIPELINE-ASSESSMENT-V2.md`, `docs/release/MANUAL-PIPELINE-READINESS-REGISTER.md`, ADR 0016, `docs/OPEN-OPERATOR-DECISIONS.md`; `TASK-STATE-TRANSITION-CONTRACT.md` in `inscope-labs/build-engine` |

---

## Context

The operator answered the Manual Pipeline decisions on 2026-11-18. This ADR records the answers as given.
Anything left blank is listed at the end and is not decided.

## Decisions

**Module approval (Decision 7, A8).** The Manual Pipeline module is approved through this ADR. The **direct-GitHub
approach is rejected**: the module talks to Gateway A only and never calls GitHub, Gateway B or Gateway C
directly.

**Decision 1 — submission and seal (C1, option a).** `SET_DONE` stays deferred (ADR 0016 D-1 Option A). The
console never marks a task `done`. A task becomes `done` only when the existing build-engine seal path says so.

**Decision 2 — archive handling (C2, option b).** The module accepts a prepared ZIP and validates its contents.
It does not build the archive from selected files.

**Decision 3 — task lease (C3, option b).** No server-side lease. The existing optimistic guards (expected head,
nonce, idempotency) are relied on. Revisit if two operators contend for one task.

**Decision 4 — credential for the seal fact (C4, option a).** Gateway A reads Gateway C's seal fact with a
**separate read-only credential for that one route**. This is new work in Gateway C and in the deployment
configuration. It is needed before a staging pilot, not for the PoC.

**Decision 5 — legal transitions, partly (C5 items 1 to 3).** Declared:
1. Nothing leaves `done`, `dropped` or `superseded`.
2. `done` is reachable only through `SET_DONE`; `SET` to `done` is refused.
3. A request for the status the task already has is **refused**, not treated as success.

## Still open (not decided)

- **C5 item 4:** which gateway wire status is `planned` (`ready`, `draft` or either), which is the legal source
  for `SET_IN_PROGRESS`.
- **C5 item 5:** whether to add contract revision 1.4 with the new error codes of section 7 of the transition
  contract, or to reuse existing codes.
- **Decision 6 (C6):** whether `done` alone is sufficient handoff evidence, or Gateway A exposes a seal indicator
  (BE-MP-11).

## Consequences for the gateway PoC work

Declared items 1 to 3 say a status-transition request is refused when the task is already in the requested
status or is in a terminal state. Without C5 items 4 and 5 the gateway has no source-status mapping and no
refusal code for those cases, so `POST /plans/{id}/status` (gateway PR 2) must still not implement a
source-state rule. Gateway PR 2 can proceed without one, and the missing rule is carried in
`docs/OPEN-OPERATOR-DECISIONS.md`.
