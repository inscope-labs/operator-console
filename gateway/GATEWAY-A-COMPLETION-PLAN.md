# Gateway A — 100% Completion Implementation Plan

**Repository:** `inscope-labs/operator-console`  
**Component:** `gateway/` (Gateway A — Operator Gateway, Cloudflare Worker)  
**Plan revision:** 1.0  
**Baseline:** RELEASE-PLAN.md (5 November 2026) + current `main` source tree  
**Objective:** Bring Gateway A from its present **partial / not deployed** state to **implemented, deployed to staging, and verified** so that it can participate in the 0.6.0 release acceptance gates.

This plan is intentionally exhaustive. Every stub, missing guard, missing Durable Object behaviour, missing audit path, missing CI gate, and missing documentation item listed in the RELEASE-PLAN baseline is allocated to a concrete stage. No work is left as “later”.

---

## 0. Governing Rules (apply to every stage)

| ID | Rule |
|----|------|
| R-01 | One stage per agent/human run. Touch only the paths listed for that stage. |
| R-02 | Fail closed. Missing configuration, unavailable Durable Object state, ambiguous policy or unverifiable input → refusal with the correct error code. |
| R-03 | Never report a consequential operation as successful because a request was accepted. Publish, upload, reconcile, halt, resume and decide have distinct terminal states. |
| R-04 | Removing a scaffold removes every `SCAFFOLD PLACEHOLDER` marker and every `TODO(...)` that belonged to it. A stage is not complete while either remains. |
| R-05 | No secrets, keys, capabilities, nonces, operator tokens or attestation material in source, fixtures, logs, reports or chat. |
| R-06 | Do not invent endpoints, fields or error codes. Authority is `docs/gateway-contract.md` and the schemas produced in Phase 1. |
| R-07 | CI is the only verifier for TypeScript. Agent reports that say “unverified in-agent” do not close a stage. |
| R-08 | Every phase ends with a **Phase Completion Report** (template in Appendix A). The report is the sole evidence that the phase gate may be signed. |
| R-09 | Protected paths (`.github/workflows/*.yml`, etc.) are changed only by the designated human process; agents report the required change and stop. |
| R-10 | Staging environment support (`ENVIRONMENT=staging`) must exist before any real deploy stage. |

---

## Phase 1 — Foundations, Contract Alignment & CI Hardening

**Goal:** Eliminate the two defects called out in RELEASE-PLAN A.4, make configuration and CI fail-closed, and produce machine-readable schemas that the rest of the plan can rely on.

### Stage 1.1 — Fix publish stub response (T1-103)

**Paths:** `gateway/src/routes/` (publish handler), any related test files.

**Work:**
- Change `POST /tasks/:id/publish` so that, while the transaction is still unimplemented, it returns **HTTP 501** with a stable error code (`NOT_IMPLEMENTED` or the catalogue equivalent) and a body that never claims success.
- Add a unit test that asserts the 501 behaviour.
- Remove any language that could be interpreted as “accepted”.

**Exit criteria:**
- [ ] Request against the stub returns 501, never 200.
- [ ] Unit test covers the case and is green in CI.
- [ ] No `SCAFFOLD` / `TODO` left in the publish handler that claims the operation is complete.

### Stage 1.2 — Environment model (BD-1.1)

**Paths:** `gateway/src/config/`, constants, tests, `docs/`.

**Work:**
- Extend allowed `ENVIRONMENT` values to `{test, staging, production}`.
- Update Zod schema, health response, and any environment-sensitive policy checks.
- Document the three environments and their intended attestation / tier policy differences.

**Exit criteria:**
- [ ] `ENVIRONMENT=staging` is accepted by the config parser.
- [ ] Health endpoint reports the correct environment.
- [ ] Unit tests cover all three values and the rejection of unknown values.
- [ ] Documentation updated.

### Stage 1.3 — Contract schemas & error catalogue freeze

**Paths:** `gateway/docs/`, `gateway/src/errors/`, parent `docs/gateway-contract.md` (coordination only).

**Work:**
- Produce or complete JSON Schema (or equivalent) for every request/response body defined in the contract.
- Freeze the public error catalogue; every code used in later stages must already exist or be added here with an ADR note.
- Ensure `docs/error-catalogue.md` and `src/errors/catalogue.ts` are consistent.

**Exit criteria:**
- [ ] Schemas exist for health, plans, tasks, branches, enrol, publish, upload, reconcile, halt, resume, sync, doctor and decide.
- [ ] Catalogue and TypeScript union are identical.
- [ ] Conformance vectors can reference the schemas.

### Stage 1.4 — CI hardening (fail-closed)

**Paths:** `.github/workflows/` (report only — human applies), `gateway/package.json`, lockfile generation.

**Work:**
- Generate and commit `gateway/package-lock.json`.
- Replace every `|| true` in the gateway CI workflow with real failure.
- Require `npm ci`, `tsc --noEmit`, `vitest run`, `verify-log-hygiene` and `check-repo-privacy` to pass.
- Rename workflows from `cbridge-*` to `gateway-*` if still pending (report the rename).

**Exit criteria:**
- [ ] CI fails the job on any of the above steps failing.
- [ ] Lockfile present and reproducible.
- [ ] Agent report lists the exact workflow changes required for the human PR.

### Stage 1.5 — Phase 1 Completion Report

Produce the report using Appendix A. Include:
- SHA of the commit(s) that closed Stages 1.1–1.4.
- CI run URLs / green status for the gateway workflow.
- Explicit statement that the publish-stub 501 and staging environment are live.
- Any residual risks or deferred items (must be empty for Phase 1).

**Phase 1 gate:** Operator signs the Phase 1 Completion Report.

---

## Phase 2 — Core Enforcement Primitives

**Goal:** Complete the Durable Objects, nonce lifecycle, capability verification edge cases, and the remaining guards so that every privileged route can rely on a correct enforcement core.

### Stage 2.1 — NonceStore completion & cleanup alarm

**Paths:** `gateway/src/durable-objects/nonce-store.ts`, related tests.

**Work:**
- Implement durable single-use nonce recording with TTL.
- Implement the cleanup alarm / scheduled deletion of expired nonces.
- Guarantee that a replayed nonce always returns `NONCE_REPLAYED` (409) even under concurrent requests.
- Add conformance and unit tests for race, expiry and cleanup.

**Exit criteria:**
- [ ] Concurrent identical capabilities produce exactly one success and one `NONCE_REPLAYED`.
- [ ] Expired nonces are removed by the alarm and do not accumulate unbounded.
- [ ] Tests green in CI.

### Stage 2.2 — EnrolmentRegistry completion

**Paths:** `gateway/src/durable-objects/enrolment-registry.ts`, enrolment routes, tests.

**Work:**
- Persist single-use challenges (TTL 300 s).
- Persist enrolment records `{kid, alias, publicKey, authTier, status, enrolledAt, leafCertSha256}`.
- Support rotate (retire old kid, activate new).
- Internal-only fetch routes; no public admin surface.
- Full unit + conformance coverage.

**Exit criteria:**
- [ ] Challenge reuse after success or expiry fails with `ENROLL_CHALLENGE_INVALID`.
- [ ] Rotate correctly retires the previous kid.
- [ ] Registry survives Worker restarts (Durable Object semantics verified by test).

### Stage 2.3 — Remaining guards (G5–G8, G11, G14, G15)

**Paths:** `gateway/src/guards/`, `docs/guard-set.md`, tests.

**Work:**
- Implement every remaining guard listed in the baseline as a stub.
- Wire them into the privileged operation pipeline in the order required by the specification.
- Document each guard’s inputs, failure code and audit fields.
- Unit tests for pass and every distinct failure mode.

**Exit criteria:**
- [ ] `docs/guard-set.md` is no longer a placeholder; every guard G1–G16 (or the authoritative set) has a description and implementation status.
- [ ] No guard remains a no-op or `TODO`.
- [ ] Tests prove each guard can independently refuse a request.

### Stage 2.4 — Capability verification edge cases

**Paths:** `gateway/src/capability/`, tests.

**Work:**
- Cover TTL_TOO_LONG, BAD_FIELD_COUNT, unknown kid, bad signature, head mismatch, wrong operation, wrong branch pattern, clock-skew boundary (±10 s).
- Ensure Model B (console signs, gateway verifies) is the only production path; any Model A residual is behind a clear interface and disabled by default.

**Exit criteria:**
- [ ] Every error code in the capability section of the catalogue is exercised by a test.
- [ ] Clock-skew tests use an injectable clock.

### Stage 2.5 — Phase 2 Completion Report

Report must include:
- List of every guard with its final status (implemented / test SHA).
- NonceStore and EnrolmentRegistry test evidence (including concurrency and alarm).
- Confirmation that no capability verification path returns success on invalid input.

**Phase 2 gate:** Operator signs the Phase 2 Completion Report.

---

## Phase 3 — Privileged Write Operations

**Goal:** Replace every write stub with a real, guarded, audited transaction that uses the GitHub App credential only after all guards and the capability have passed.

### Stage 3.1 — Transaction & rate-coordinator primitives

**Paths:** `gateway/src/` (new or existing transaction / rate modules), Durable Objects as needed.

**Work:**
- Introduce a reusable transaction helper that:
  - Records the nonce first,
  - Runs the ordered guard set,
  - Performs the GitHub mutation,
  - Emits the audit record,
  - Returns a structured outcome (never a bare 200 on partial work).
- Implement a simple rate / concurrency coordinator if required by the specification (or document why it is unnecessary).

**Exit criteria:**
- [ ] Transaction helper is used by every subsequent write stage.
- [ ] Failure at any step leaves a consistent state (nonce consumed or not according to policy, audit emitted on both success and refused-after-guards cases as required).

### Stage 3.2 — Publish transaction

**Paths:** publish route, adapter, tests.

**Work:**
- Replace the 501 stub with the real publish transaction.
- Enforce task-branch pattern, head SHA, all applicable guards.
- Return the contract success shape with a real `auditRef`.
- Conformance vector for happy path and every documented refusal.

**Exit criteria:**
- [ ] Successful publish produces a verifiable GitHub side-effect (or a clearly mocked side-effect in unit tests) and a durable audit reference.
- [ ] All refusal paths return the correct catalogue codes.
- [ ] No 501 remains for publish.

### Stage 3.3 — Upload

**Paths:** upload route, adapter, tests.

**Work:**
- Accept the zip payload (size limits, SHA-256 check).
- Store or forward according to the contract.
- Full guard + capability + audit path.

**Exit criteria:**
- [ ] Upload succeeds only with valid capability and matching head.
- [ ] Oversized or mismatched SHA payloads are refused with stable codes.
- [ ] Audit record emitted.

### Stage 3.4 — Reconcile

**Paths:** reconcile route, adapter, tests.

**Work:**
- Implement merge / reconcile logic under the same transaction model.
- Outcome field matches contract (`MERGED:pr-N` or equivalent).

**Exit criteria:**
- [ ] Happy path and refusal paths covered.
- [ ] Audit reference returned.

### Stage 3.5 — Halt & Resume

**Paths:** halt / resume routes, engine-branch handling, tests.

**Work:**
- Capability must be engine-scoped.
- Persist halt state so subsequent privileged operations can be refused while halted.
- Resume clears the state only with a valid capability.

**Exit criteria:**
- [ ] While halted, publish/upload/reconcile are refused with the correct code.
- [ ] Resume restores normal operation.
- [ ] Both actions produce audit records.

### Stage 3.6 — Decide (if in contract scope)

**Paths:** decisions route, tests.

**Work:**
- If BD-1.3 / Stage 1.3 added a decide endpoint, implement it fully; otherwise mark the route as explicitly out-of-contract and return 501 with a clear message.
- Attribution, rationale, scope and evidence fields required.

**Exit criteria:**
- [ ] Either fully implemented against the frozen contract or permanently 501 with documentation that it is out of scope for 0.6.0.

### Stage 3.7 — Phase 3 Completion Report

Report must list every privileged operation with:
- Final status (implemented / out-of-scope),
- Test evidence (unit + conformance),
- Sample auditRef format,
- Confirmation that no write path can succeed without a consumed nonce and a passed guard set.

**Phase 3 gate:** Operator signs the Phase 3 Completion Report.

---

## Phase 4 — Read Completeness, Diagnostics & Admin Surface

**Goal:** Finish every read and diagnostic endpoint, PlanDO, ETag/cache behaviour, and any internal admin routes required for operations.

### Stage 4.1 — PlanDO & plan/task read paths

**Paths:** `gateway/src/durable-objects/` (PlanDO), plan/task routes, tests.

**Work:**
- Implement PlanDO storage and retrieval semantics required by the status and next-task endpoints.
- Ensure ETag / conditional request behaviour if specified.
- Complete any remaining gaps in `/plans`, `/plans/{id}/status`, `/tasks/next`, validate, render-check.

**Exit criteria:**
- [ ] All read endpoints in the contract return real data or the documented empty/error shapes.
- [ ] PlanDO survives restarts and concurrent readers.

### Stage 4.2 — Sync & Doctor

**Paths:** sync and doctor routes, tests.

**Work:**
- `POST /sync` performs the tracking-branch check and returns the contract shape.
- `GET /doctor` runs the full diagnostic suite (token validity, ruleset, halt state, repo privacy, etc.) and returns structured results.

**Exit criteria:**
- [ ] Doctor never leaks secrets in its output.
- [ ] Both endpoints covered by tests.

### Stage 4.3 — Admin / internal surfaces

**Paths:** `gateway/src/routes/admin.ts` (or equivalent), tests.

**Work:**
- Implement only the internal routes required for enrolment inspection, revocation or operational control that the specification demands.
- No public unauthenticated admin surface.
- All actions audited.

**Exit criteria:**
- [ ] Admin routes are reachable only with the correct operator credential (and any additional internal checks).
- [ ] Documentation lists every admin path and its purpose.

### Stage 4.4 — Phase 4 Completion Report

Report must confirm that every endpoint listed in `docs/gateway-contract.md` is either fully implemented or explicitly marked out-of-scope for 0.6.0 with a 501 response.

**Phase 4 gate:** Operator signs the Phase 4 Completion Report.

---

## Phase 5 — Audit, Observability & Hygiene

**Goal:** Make the audit trail complete, redaction enforced, and operational hygiene scripts part of the definition of done.

### Stage 5.1 — Audit record, emit and stream

**Paths:** `gateway/src/audit/`, AuditStream Durable Object, every write path.

**Work:**
- Every privileged success and every post-guard refusal writes a durable audit record.
- AuditStream Durable Object (or equivalent) supports ordered retrieval if required.
- Redaction gate guarantees that secrets, keys, nonces, challenges and signatures never appear in logs or audit payloads.

**Exit criteria:**
- [ ] Audit reference returned by write operations can be resolved to a record.
- [ ] `verify-log-hygiene` passes on the final code.
- [ ] Unit tests prove redaction for known sensitive fields.

### Stage 5.2 — Rate limiting, ETag cache & residual primitives

**Paths:** any remaining rate / cache modules.

**Work:**
- Implement or permanently document the absence of any remaining “rate coordinator” or “ETag cache” items called out in the baseline.
- If implemented, add tests; if deliberately omitted, record the decision in an ADR or the Phase report.

**Exit criteria:**
- [ ] No open “TODO: rate coordinator” or “TODO: ETag” remains in source.

### Stage 5.3 — Phase 5 Completion Report

Report must include:
- Sample redacted audit record,
- Confirmation that log-hygiene and repo-privacy scripts are green,
- List of any deliberately omitted primitives with justification.

**Phase 5 gate:** Operator signs the Phase 5 Completion Report.

---

## Phase 6 — Deployment, Staging Verification & Runbook

**Goal:** Deploy Gateway A to a real staging environment, prove the end-to-end operator flows, and produce the operational runbook.

### Stage 6.1 — Deploy workflow hardening

**Paths:** `.github/workflows/gateway-deploy.yml` (report required changes).

**Work:**
- Replace the scaffold deploy workflow with a real, approval-gated pipeline.
- Require V1–V12 (or the authoritative verification suite) against staging before any production promote.
- Secrets only from the staging GitHub Environment / Cloudflare dashboard.

**Exit criteria:**
- [ ] Workflow no longer contains `TODO: run V1–V12\ldots`.
- [ ] Human PR merges the workflow changes.

### Stage 6.2 — Staging deployment

**Paths:** Cloudflare Worker, secrets, custom domain or workers.dev route.

**Work:**
- Deploy the Worker that corresponds to a green CI SHA.
- Configure `ENVIRONMENT=staging`, all secrets, Durable Object bindings and GitHub App installation for the staging repositories.
- Smoke-test `/health` and a non-mutating authenticated endpoint.

**Exit criteria:**
- [ ] Health returns `environment: "staging"`.
- [ ] Operator token authentication succeeds.
- [ ] Deployment SHA recorded.

### Stage 6.3 — End-to-end verification suite against staging

**Work:**
- Execute the full conformance / acceptance suite (V1–V12 or successor) against the live staging Worker.
- Cover: enrol → mint capability → publish / upload / reconcile / halt / resume (as implemented) → audit resolution → nonce replay refusal → head-drift refusal.
- Capture raw request/response evidence (redacted) and CI or manual run logs.

**Exit criteria:**
- [ ] Every implemented privileged operation has at least one successful and one refused evidence record.
- [ ] No unexpected 5xx or leaked secrets in responses.

### Stage 6.4 — Runbook & operational documentation

**Paths:** `gateway/docs/`, parent operator guide.

**Work:**
- Write the staging and production runbook: secret rotation, key enrolment, emergency halt, audit inspection, rollback.
- Update architecture.md, guard-set.md, error-catalogue.md and capability-format.md so none remain placeholders.

**Exit criteria:**
- [ ] A new operator can follow the runbook to enrol a device and perform a publish against staging without reading source code.
- [ ] All gateway docs are free of `TODO` / placeholder text.

### Stage 6.5 — Phase 6 Completion Report

Report must contain:
- Staging Worker URL and deployment SHA,
- Full evidence index for the verification suite,
- Runbook location,
- Explicit statement that Gateway A is ready for the joint 0.6.0 pilot (Phase 7 of the parent RELEASE-PLAN).

**Phase 6 gate:** Operator signs the Phase 6 Completion Report.  
**At this point Gateway A is considered 100% complete for the 0.6.0 scope.**

---

## Phase 7 — Joint Pilot Support (Coordination Only)

This phase is owned by the parent RELEASE-PLAN (system-complete gate). Gateway A’s obligation is:

- Remain stable on the staging SHA signed in Phase 6.
- Provide any additional audit or diagnostic endpoints the pilot requires (change-controlled via a new stage if needed).
- Participate in the evidence archive that lets the operator sign the Termux-retirement matrix.

No new feature work is planned under this document once Phase 6 is signed.

---

## Appendix A — Phase Completion Report Template

```markdown
# Gateway A — Phase N Completion Report

- **Phase:** N — <title>
- **Date (UTC):** YYYY-MM-DD
- **Author:** <agent or human>
- **Baseline SHA:** <git SHA at start of phase>
- **Closing SHA(s):** <git SHA(s) that contain the phase work>

## 1. Stages closed

| Stage | Title | Closing SHA | CI run / evidence |
|-------|-------|-------------|-------------------|
| N.1   | \ldots | \ldots      | \ldots            |
| \ldots| \ldots | \ldots      | \ldots            |

## 2. Exit criteria checklist

Copy every exit-criterion bullet from the phase and mark [x] or [ ] with a one-line evidence pointer.

## 3. Residual risks / deferred items

Must be empty for the phase to be signable. If any item remains, list it and the stage that will close it.

## 4. Security & hygiene

- [ ] No secrets in source or report
- [ ] `verify-log-hygiene` green
- [ ] `check-repo-privacy` green (if applicable)

## 5. Operator sign-off

- **Decision:** PASS / FAIL
- **Signed by:** <operator>
- **Date:** 
- **Notes:** 
```

---

## Appendix B — Traceability Matrix (Baseline → Stage)

| Baseline gap (RELEASE-PLAN A.4)              | Closing stage(s)   |
|----------------------------------------------|--------------------|
| Publish returns 200 on stub                  | 1.1                |
| ENVIRONMENT lacks staging                    | 1.2                |
| Contract / schema drift                      | 1.3                |
| CI permissive, no lockfile                   | 1.4                |
| NonceStore incomplete / no cleanup alarm     | 2.1                |
| EnrolmentRegistry incomplete                 | 2.2                |
| Guards G5–G8, G11, G14, G15                  | 2.3                |
| Capability edge cases                        | 2.4                |
| Transaction / rate coordinator               | 3.1, 5.2           |
| Publish transaction                          | 3.2                |
| Upload                                       | 3.3                |
| Reconcile                                    | 3.4                |
| Halt / resume                                | 3.5                |
| Decide                                       | 3.6                |
| PlanDO, ETag cache                           | 4.1, 5.2           |
| Sync / doctor                                | 4.2                |
| Admin surface                                | 4.3                |
| Audit record / emit / stream                 | 5.1                |
| Deploy workflow scaffold                     | 6.1                |
| Not deployed                                 | 6.2                |
| No end-to-end staging evidence               | 6.3                |
| Placeholder docs                             | 6.4                |

---

## Appendix C — Definition of “100% Complete”

Gateway A is 100% complete for the 0.6.0 scope when:

1. Every stage in Phases 1–6 has a signed Phase Completion Report.
2. The staging deployment is live, healthy, and has passed the full verification suite.
3. The traceability matrix above contains no open rows.
4. The parent RELEASE-PLAN can treat Gateway A as an available dependency for the system-complete (pilot) gate.

Any subsequent scope expansion (new operations, new guards, production cut-over) is a new plan revision.
```
