# Plan Management Authoring (PMA) — Readiness Register

**Document ID:** `OC-PMA-PRR-001`
**Repository:** `inscope-labs/operator-console`
**Revision:** `1.0`
**Install path:** `docs/release/PMA-READINESS-REGISTER.md`, via a branch and pull request that the operator merges
**Verification head:** `2c450d2` (`main` on 2026-11-16; merge of PR #76). `android-ci` is green on this commit
**Scope:** the plan authoring interface added in PR #76: draft model and device store, canonical validation and preview, the authoring screens, and the submit action. Detail and test counts are in `docs/STATUS.md` (“Plan Management Authoring (PMA) detail”)
**Release gate status:** **NOT READY.** PMA works as a local draft editor. It cannot submit a plan, so no pull-request acceptance criterion can be met (see SUB)

**Status vocabulary.** `UNVERIFIED` (no closure evidence), `BLOCKED` (cannot proceed until a named dependency exists), `FINDING` (a gap or defect observed at the verification head), `PASS` (evidence attached and independently reviewable). “Code exists” and “JVM tests pass” are not closure evidence on their own. “Observed” notes are reviewer observations at `2c450d2`.

**Dependency order.** `GOV → DAT → VAL → UI → SUB → SEC → TST`.

## GOV — Governance

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| GOV-01 | An ADR records the PMA decisions: draft schema, API contract, error model, size limits, authorisation model, identifier reservation, draft retention, and the distinction between a draft record, a repository plan and the `PlanDO` read projection. | Merged ADR. Observed: none exists (`docs/STATUS.md`, OPEN 1). | FINDING | Operator | — |
| GOV-02 | `docs/STATUS.md` reflects the merged state of PR #76. | Observed at `2c450d2`: it still said “Not merged”. Corrected in the PR that installs this register. | UNVERIFIED | Operator | — |
| GOV-03 | PMA is mapped to the controlling plan: its place relative to `RELEASE-PLAN.md` stages is recorded. | Stage mapping in the ADR. Observed: PMA arrived outside the staged plan, as the manual pipeline did. | FINDING | Operator | GOV-01 |

## DAT — Draft model and storage

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| DAT-01 | A draft mirrors the canonical plan directory field for field: `plan.json`, `stages.json`, `tasks/*.json`, `coverage_exempt.json`. | Round-trip test against the build-engine schemas in `template/engine/schemas/`. Observed: the draft emits `executor` and `status` on every task; the field-by-field comparison was partial. | UNVERIFIED | Feature Owner | — |
| DAT-02 | Task identifiers are stable under reordering: `NNNNN-PHASE-TTT-SS`, stage from the hundreds digit. | Observed: `PlanAuthoringViewModelTest` covers “moving a task does not change its identifier”; `PlanDraftAllocatorTest` (13) covers derivation. | UNVERIFIED | Feature Owner | DAT-01 |
| DAT-03 | Task status vocabulary is documented against the console wire statuses. Drafts use the build-engine set (`planned`, `in_progress`, `pr_open`, `done`, `dropped`, `superseded`); the gateway wire set differs (`draft`, `ready`, `in_progress`, `completed`, `blocked`). | A documented mapping; see `OC-MP-PRR-001` LIF-01. | FINDING | Feature Owner | GOV-01 |
| DAT-04 | The device draft store (`PlanDraftStore`, a Preferences DataStore) persists, reloads and deletes drafts correctly, including after process death. | Instrumented test on a device. Observed: JVM tests use an in-memory repository, so the real store has not run off-device. | UNVERIFIED | QA Lead | — |
| DAT-05 | A retention and size policy exists for drafts (maximum size, number of drafts, deletion). | Policy in the ADR plus a test. Observed: none recorded. | FINDING | Operator | GOV-01 |
| DAT-06 | Drafts contain no secrets and the store holds no capability or credential. | Schema review and test. Observed: the store is a plain Preferences DataStore; confirm whether draft text can carry sensitive content before accepting that. | UNVERIFIED | Security Champion | DAT-05 |

## VAL — Canonical validation and preview

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| VAL-01 | The local validator applies the canonical rule set. | Observed: `plan_id` (`^[a-z0-9][a-z0-9-]*$`), `plan_number` (`^[0-9]{5}$`), task id shape, at least one acceptance criterion per task, deliverables, verify, safe relative paths, deliverable roots and patterns, ascending stage order, dependency resolution and cycles are checked, matching the schemas compared. A full schema-by-schema diff is still required. | UNVERIFIED | Feature Owner | DAT-01 |
| VAL-02 | The validator and the canonical validator cannot drift: both run against shared vectors. | A shared conformance vector set run in CI on both sides. Observed: none. | FINDING | Feature Owner | VAL-01 |
| VAL-03 | A local pass is never presented as the canonical verdict. | Observed: the submit action reports the canonical server-side validator as unavailable. UI test needed. | UNVERIFIED | QA Lead | UI-03 |
| VAL-04 | The preview materialises exactly the files a submission would create. | Observed: `PlanAuthoringViewModelTest` “the preview is the set of files a submission would create”. Add a byte-level comparison with canonical tooling output. | UNVERIFIED | QA Lead | DAT-01 |
| VAL-05 | The plan number is reserved by the authoritative allocator (`plans/counter.json`). | Reservation route and test. Observed: PMA does not mint a number; identifiers carry a visible `<NNNNN>` placeholder and `isPlanNumberReserved` stays false, so two drafts can collide. | BLOCKED (allocator route) | Feature Owner | SUB-01 |

## UI — Authoring screens

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| UI-01 | The plan list shows drafts beside gateway plans and opens a draft through `Routes.planAuthoringDraft`. | Observed: the route is wired in `AppNavHost`. Device walkthrough needed. | UNVERIFIED | QA Lead | DAT-04 |
| UI-02 | Create, edit, save, reopen and discard work, and unsaved changes are flagged and not lost on back navigation or rotation. | Observed: 17 view-model tests cover create, edit, save, reopen and discard. Compose and rotation behaviour is untested. | UNVERIFIED | QA Lead | DAT-04 |
| UI-03 | Every blocked state shows its reason: validation errors with field paths, “number not reserved”, and the submit blockers. | UI test per state. | UNVERIFIED | QA Lead | VAL-03 |
| UI-04 | Accessibility and small-screen layout are acceptable (the Compose sections are about 620 lines). | Device screenshots and an accessibility pass. | UNVERIFIED | QA Lead | UI-01 |

## SUB — Submission

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| SUB-01 | Gateway A exposes a plan-authoring route (draft store, revision compare-and-swap, submission request, status lookup) defined in a contract revision first (I-10). | Contract revision plus gateway implementation. Observed: `PUT /plans/{id}` and `POST /plans/{id}/validate` act on the simplified catalogue record, not the canonical plan store. | BLOCKED | Gateway owner | GOV-01 |
| SUB-02 | A Build Engine workflow materialises a submitted plan into `plans/<plan_id>/` and opens the reviewable pull request. | Workflow plus conformance test. Observed: none exists. | BLOCKED | Build Engine owner | SUB-01 |
| SUB-03 | The submit action fails closed and states exactly what is missing. | Observed: `SubmitPlanDraftUseCase` sends nothing and lists its blockers; `PlanDraftUseCasesTest` covers it. Keep when SUB-01 and SUB-02 land. | UNVERIFIED | QA Lead | — |
| SUB-04 | A plan is reported accepted only after the repository change is merged and confirmed. | End-to-end test. | BLOCKED | QA Lead | SUB-02 |
| SUB-05 | A submission write goes through the minting path, the journal and the shared executor, with an idempotency key. | Architecture test (I-01 to I-03). | BLOCKED | Security Champion | SUB-01 |

## SEC — Security

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| SEC-01 | No GitHub credential path and no storage permission is added (ADR 0002). | Observed: the DI module binds no HTTP surface and the manifest is unchanged. Architecture test needed. | UNVERIFIED | Security Champion | — |
| SEC-02 | Draft content and validation messages are redacted in logs. | Redactor test. | UNVERIFIED | Security Champion | DAT-06 |
| SEC-03 | The authoring surface respects the authorisation model: who may author or submit, per environment (I-11). | Defined in the ADR, then tested. | BLOCKED | Security Champion | GOV-01 |

## TST — Testing

| ID | Readiness check | Acceptance evidence | Status | Owner | Depends on |
|---|---|---|---|---|---|
| TST-01 | JVM suite for validation, allocation, use cases and view model passes in CI. | Observed: 69 tests (validator 24, allocator 13, use cases 15, view model 17); `:app:testDebugUnitTest` 747/747 recorded in `docs/STATUS.md`. | UNVERIFIED | QA Lead | — |
| TST-02 | Instrumented tests cover the store, navigation and the authoring screens on a device. | Instrumented report. Observed: no PMA instrumented tests found. | FINDING | QA Lead | DAT-04 |
| TST-03 | The known flaky test is fixed or quarantined. | Observed: `SettingsViewModelTest > saveCredential and clearCredential update state` failed once on the same SHA and passed on rerun. It is outside PMA but sits in the same gate. | FINDING | Android Lead | — |
| TST-04 | Property or fuzz tests cover the validator and allocator (stable ids under reorder, no collisions, cycle detection). | Test report. | UNVERIFIED | QA Lead | VAL-01 |
| TST-05 | End-to-end: author, validate, submit, pull request opened, merge confirmed. | E2E report. | BLOCKED | QA Lead | SUB-04 |

## Reading the register

- PMA can be used today only to write and preview drafts. It must not be described as able to create plans.
- `BLOCKED` rows depend on gateway and Build Engine work outside this feature; the console cannot add those routes itself (I-10).
- A `FINDING` or `BLOCKED` row is never accepted by default. Release needs every row `PASS` or an operator-accepted risk recorded in writing.

## Not covered

Gateway A and Build Engine source, the manual pipeline module (see `OC-MP-PRR-001`), and plan reading (`PlanListScreen`, `PlanDetailScreen`) beyond the draft list entry point.
