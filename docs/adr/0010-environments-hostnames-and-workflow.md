# ADR 0010 — Environments, Hostnames, and Repository Workflow

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-02 |
| **Decision IDs** | C-5, C-5b, D-7 (operator-console) |
| **Related** | build-engine ADR 0008 and ADR 0009 |

---

## Decision
1. **Environments (C-5, option a):** `test` (local only), `staging`, and `production`. No production hostname is baked into source; configuration is validated at runtime.
2. **Hostnames:** the operator supplied `qnuralink.workers.dev` for Gateway A in staging and in production, which also means production on `workers.dev` (see build-engine ADR 0008). As far as the platform's naming scheme goes, each Worker is served at `<worker-name>.<account-subdomain>.workers.dev`, so the exact Gateway A hostnames are fixed at first deployment and recorded then.
3. **Hosting scope (C-5b):** the ADR 0001 decision (Cloudflare Workers) covers Gateway A; recorded in build-engine ADR 0008.
4. **Repository workflow:** `main` is unprotected and stays that way. The AI Studio agent created this repository and pushes only to `main`; no protection, ruleset or required check that blocks it may be applied without a new decision. Claude's changes go on branches and pull requests that the operator merges.

## Consequences
- The shared zone's mitigations are those in ADR 0007 item 2.
- Production hostname validation at runtime must accept the `workers.dev` host only once the build-engine gates are changed.

## Operator authorization
Recorded from the operator's completed decisions questionnaire, uploaded 2026-11-02. Approver: John Wendell, operator and lead architect, inscope-labs. Decisions closed as answered; Claude may open pull requests touching only decision records (`docs/**`) and does not merge, tag, release or push to `main`; the session token is used for this assignment only. Nothing on Cloudflare is created, changed or deployed and no secret is handled in this assignment.
