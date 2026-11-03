# ADR 0008 — Task-State Model and cbridge Scope

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-02 |
| **Decision IDs** | C-2, C-3 |
| **Related** | build-engine ADR 0006 (D-3, D-3b, D-4) |

---

## Decision
1. **Task state (C-2):** the same as build-engine ADR 0006 item 7. The gateway adds an explicit, guarded status-transition operation. `SetTaskStatusUseCase` implements it and never refuses silently.
2. **cbridge scope (C-3):** the same as build-engine ADR 0006 item 5. Local `init`, `bootstrap`, `verify` and shell stay out of the app. Each operator workflow gets a documented alternative in `CBRIDGE-COVERAGE-MATRIX.md` (build-engine).
3. **Retirement:** the Termux `cbridge` is retired only after OC-8 and build-engine BE-7 pass and the operator signs.

## Consequences
- The operation contract is defined once, in build-engine's `CAPABILITY-CONFORMANCE.md`, and consumed here.

## Operator authorization
Recorded from the operator's completed decisions questionnaire, uploaded 2026-11-02. Approver: John Wendell, operator and lead architect, inscope-labs. Decisions closed as answered; Claude may open pull requests touching only decision records (`docs/**`) and does not merge, tag, release or push to `main`; the session token is used for this assignment only. Nothing on Cloudflare is created, changed or deployed and no secret is handled in this assignment.
