# ADR 0009 — Operation Journal and Background Executor

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-02 |
| **Decision IDs** | C-4 |
| **Related** | `docs/V0.6.0-BUILD-PLAN.md` OC-4 |

---

## Decision
The durable operation journal is an **encrypted Room database**, and background work runs on **WorkManager**.

## Consequences
- Both need changes to the protected build files (dependencies, build configuration). Claude, not the AI Studio agent, makes that change, in a pull request that the operator merges. That change is **not** covered by the current assignment's authorized paths and needs its own authorization.
- AI Studio pushes directly to `main` (ADR 0010); a build-file pull request may need conflict resolution at merge.

## Operator authorization
Recorded from the operator's completed decisions questionnaire, uploaded 2026-11-02. Approver: John Wendell, operator and lead architect, inscope-labs. Decisions closed as answered; Claude may open pull requests touching only decision records (`docs/**`) and does not merge, tag, release or push to `main`; the session token is used for this assignment only. Nothing on Cloudflare is created, changed or deployed and no secret is handled in this assignment.
