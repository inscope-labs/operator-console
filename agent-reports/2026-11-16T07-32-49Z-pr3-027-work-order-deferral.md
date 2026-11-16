# PR3-027 — Work-Order Creation: Documented Alternative, Not Implementation

- **Task:** `PR3-027` (`RELEASE-PLAN.md` Stage 3.4 / `T3-404`; BD-1.3; `docs/release/PHASE-3-REMEDIATION-REGISTER.md` rev 1.2)
- **Timestamp (UTC):** `2026-11-16T07:32:49Z`
- **Worked-from HEAD SHA:** `560882c` (`main` at run start — the merge of PR #72 / PR3-025; the branch was rebased onto it before the PR was finalised)
- **Branch:** `pr3/pr3-027-work-order-deferral`
- **Pull request:** #73 (draft at first push, to claim the register ID)
- **Operator decision relied on:** ADR 0014 D5 — BD-1.3 Option B confirmed (work orders stay with engine tooling)
- **Contract revision relied on:** revision 1.2 (ADR 0015), `docs/gateway-contract.md` §3.6
- **Acceptance & Closure Status:** **NOT accepted / NOT closed.** The operator or designated reviewer closes PR3-027 after inspecting this evidence. No task is marked ACCEPTED here.

---

## 1. Why this item was selected

The operator directed PR3-027 after PR3-025 went to another agent. The item was checked for eligibility
before any code was written:

| Gate | Result |
|---|---|
| Not `[CLAUDE]`-owned (Appendix B) | PR3-022 to PR3-027 are `[AIS]` / Stage 3.4 — eligible |
| Not blocked | A deferral needs no contract revision, no gateway route and no gateway environment. The contract *forbids* the console implementing this endpoint, which is the point |
| Not already done on `main` | Two of the three work items were outstanding — see §2 |
| Not claimed | PR #73 is the only `pr3/*` PR; PR #72 (PR3-025) touches only decide files and does not overlap |
| One narrowly scoped PR, non-protected paths only | 5 production/test files + 2 documentation files, all under `app/**`, `docs/**` and `agent-reports/**` |
| Acceptance demonstrable with available verification | JVM unit tests via `android-ci.yml` |

---

## 2. What was outstanding, and what this PR changed

The register's **Work** line for PR3-027 has three parts. Against `main` at `2904a44`:

| Work item | State before | Action |
|---|---|---|
| *Remove `CreateWorkOrderUseCase` from any operational path* | **Partly done.** The use case already failed closed, but `UseCaseModule` still **provided** it, so any ViewModel, screen or repository could have injected it | The provider and its import are removed. The use case is now out of the Hilt graph, and no production source outside its own file references it |
| *Show the documented alternative* | **Not done.** No UI surface said anything about work orders | An informational work-order card on the plan detail screen — the plan-scoped surface where a `POST /plans/{id}/order` action would have lived |
| *Record the deferral in `docs/STATUS.md` and the evidence dossier* | **Done, but written before contract revision 1.2 landed**, and the recorded wording was no longer accurate | Both records updated, and one stale line in the evidence dossier corrected |

The wording problem was real. `CreateWorkOrderUseCase.DEFERRAL_REASON` claimed *"no /orders endpoint or
order schema exists in the gateway contract"*. Against revision 1.2 that is **not accurate**: §2 still
lists `POST /plans/{id}/order` in the cbridge command mapping, and §3.6 now defines the `order` field on
the task read. What the contract does **not** do is define a §3 request or response, or a schema, for
that endpoint — and §3.6 states that *"Work orders are created by engine tooling (BD-1.3 option B); the
gateway reports the value it holds for the task."* The deferral is therefore not a gap in the contract;
it is a deliberate, contract-confirmed deferral.

---

## 3. Files added or changed

| # | File | Reason |
|---|---|---|
| 1 | `app/src/main/kotlin/com/inscopelabs/buildengine/domain/model/WorkOrder.kt` *(modified)* | Holds the canonical deferral statement (`DEFERRED_REASON`), the contract reference and the decision reference, so the domain refusal reason and the operator-facing text cannot drift apart. The KDoc is corrected against revision 1.2 |
| 2 | `app/src/main/kotlin/com/inscopelabs/buildengine/domain/usecase/write/CreateWorkOrderUseCase.kt` *(modified)* | Refuses with the canonical reason; documents that it is deliberately not in the Hilt graph and carries no dependency it could mint, journal or send with |
| 3 | `app/src/main/kotlin/com/inscopelabs/buildengine/di/UseCaseModule.kt` *(modified)* | Removes `provideCreateWorkOrderUseCase` and its import. The use case is out of the Hilt graph: no ViewModel, screen or repository can inject it |
| 4 | `app/src/main/kotlin/com/inscopelabs/buildengine/ui/plans/PlanDetailScreen.kt` *(modified)* | Shows the documented alternative — a `WorkOrderDeferralCard` with a test tag, no button, no `onClick`, nothing that can reach the gateway |
| 5 | `app/src/main/res/values/strings.xml` *(modified)* | `work_order_title` and `work_order_deferral_body` |
| 6 | `app/src/test/kotlin/com/inscopelabs/buildengine/domain/usecase/WorkOrderDeferralTest.kt` *(new)* | 12 tests: the deferral statement, the structural removals, the I-10 endpoint check, and the presence and action-freeness of the documented alternative |
| 7 | `docs/STATUS.md` *(modified — Stage 3.4 row and the `T3-404` row only)* | Records that the deferral is structural and that the documented alternative is shown. The Android-runtime and integration columns stay at `no` |
| 8 | `docs/release/PHASE-3-EVIDENCE.md` *(modified)* | The Stage 3.4 traceability line, the T3-404 / PR3-027 implementation artefact, the open-item disposition, and one stale Stage 3.3 open item (see §7) |
| 9 | `agent-reports/2026-11-16T07-32-49Z-pr3-027-work-order-deferral.md` *(new — this report)* | Required by plan rule G-01 |

### Protected / forbidden path confirmation

- **Nothing** under `.github/workflows/**`, `gateway/**`, `proguard-rules.pro`, `AndroidManifest.xml`,
  `app/build.gradle.kts`, `version.properties`, `build-logs/**`, `gradle/libs.versions.toml` or
  `network_security_config.xml` was modified.
- `RELEASE-PLAN.md`, `docs/gateway-contract.md`, `docs/schemas/**`, `docs/adr/**` and
  `docs/release/PHASE-3-REMEDIATION-REGISTER.md` were **not** modified.
- No dependency was added, removed or changed.
- The Room schema is untouched.

### Plan-rule conformance (G-01, G-02, G-13, G-14)

- **G-02 (one stage, named paths only):** Stage 3.4 has no explicit **Files:** line in `RELEASE-PLAN.md`,
  so the stage's task files are the scope. Every file touched is named by `T3-404` (`CreateWorkOrderUseCase`,
  "a documented alternative screen") or is the wiring that made the use case injectable. No new production
  file was created.
- **G-13 (scope split):** nothing under `gateway/**` was touched; the run started from the current `main`.
- **G-14 (scaffolds):** no scaffold file was recreated, renamed, moved or deleted. `WorkOrder.kt` and
  `CreateWorkOrderUseCase.kt` were completed in place and carry no `TODO("SCAFFOLD…")` marker.
- **G-01 (report):** this file.

---

## 4. How the deferral is now enforced

The register's acceptance criterion is *"No scaffolded work-order action is represented as
operational."* The previous state satisfied that only by convention — nothing called the use case, but
nothing stopped it either. It is now enforced structurally, at four independent levels:

| Level | Enforcement | Test |
|---|---|---|
| Wiring | `UseCaseModule` no longer provides `CreateWorkOrderUseCase`; it is out of the Hilt graph | `the use case is not provided by the Hilt graph` |
| Reachability | No production source outside the use case's own file references it | `no production source outside the use case file references the use case` |
| Capability | The use case declares **no** constructor parameter, so it holds no repository, minter, network client or clock — it cannot mint, journal or send even if called | `the use case has no dependency it could mint journal or send with` |
| Contract | `GatewayEndpoints` names no `/order` route and `GatewayApi` exposes no work-order creation method | `the gateway endpoint table defines no work-order creation route`, `the gateway api exposes no work-order creation method` |

And the deferral is **shown** rather than merely enforced: the plan detail screen carries a work-order
card stating that work orders are created in engine tooling, that the console does not create them, and
that the gateway reports the identifier it holds for a task while the console copies that value and never
derives or invents one. The card has a test tag, a title and a body — and no button, no `onClick`, and no
route to the gateway.

---

## 5. Acceptance criteria

### 5.1 The `[REG+]`-style clauses of PR3-027

> **Work:** BD-1.3 option B is confirmed by the operator (ADR 0014 D5). Defer work-order creation: remove
> `CreateWorkOrderUseCase` from any operational path, show the documented alternative, and record the
> deferral in `docs/STATUS.md` and the evidence dossier.

| Criterion | Status | Source / test |
|---|---|---|
| `CreateWorkOrderUseCase` removed from any operational path | **MET** | §4 — four independent structural levels, each with a test |
| The documented alternative is shown | **MET** | `WorkOrderDeferralCard` on `PlanDetailScreen`; `the documented alternative is present in the plan detail screen`, `the documented alternative carries no action` |
| The deferral is recorded in `docs/STATUS.md` | **MET** | Stage 3.4 row and the `T3-404` row |
| The deferral is recorded in the evidence dossier | **MET** | `docs/release/PHASE-3-EVIDENCE.md` — traceability line, implementation artefact, open-item disposition |

### 5.2 The remaining PR3-027 acceptance criteria

| Criterion | Status | Source / test |
|---|---|---|
| No scaffolded work-order action is represented as operational | **MET** | §4. The use case has no scaffold markers, fails closed, is unreachable and offers no action |
| If the operator had chosen to support it, an approved contract revision would define the request and response first, and the work would follow the same executor, confirmation, journal and recovery rules | **MET (not applicable branch, recorded)** | The operator chose BD-1.3 Option B (ADR 0014 D5), so this branch does not apply. It is recorded here so the disposition is explicit rather than implied |
| **Gate treatment:** a deferral under BD-1.3 Option B is a plan-consistent disposition and does not block Gate 3 | **MET (recorded)** | `docs/release/PHASE-3-EVIDENCE.md` §3 item 4 states this explicitly |

### 5.3 Evidence requested by the register

> **Evidence:** The operator's BD-1.3 decision reference and the status record.

| Evidence | Status |
|---|---|
| The operator's BD-1.3 decision reference | **Produced** — ADR 0014 D5, cited in `WorkOrder.DECISION_REFERENCE`, in the deferral reason itself, in the evidence dossier and in this report |
| The status record | **Produced** — `docs/STATUS.md` (Stage 3.4 row, `T3-404` row) and `docs/release/PHASE-3-EVIDENCE.md` |

---

## 6. Tests run and results

**Local execution: NOT PRODUCED.** This environment has no JDK, no Gradle and no Android SDK
(`java: command not found`), so `./gradlew :app:testDebugUnitTest` could not be run here. No local test
result is claimed or inferred. Verification was delegated to `android-ci.yml`.

`android-ci` run **43825323550** on the branch head `34772d9` — **success**
(<https://github.com/inscope-labs/operator-console/actions/runs/43825323550>)

| Job | Result |
|---|---|
| `unit-tests-and-debug-build` | **success** — `:app:testDebugUnitTest` `BUILD SUCCESSFUL in 1m 20s`; `:app:assembleDebug` `BUILD SUCCESSFUL in 22s` |
| `vectors-are-reproducible` | **success** |

From the retained `unit-test-reports` artifact:

```
536 tests      0 failures      0 ignored      5.158s duration      100% successful
```

The suite grew from 512 to 536 tests — the 12 added here, plus the 12 from PR3-025, which merged
into `main` while this branch was open and was rebased onto before the run above.

Earlier runs of the same code, for the record: **43824183321** on `bc711fa` — success (524 tests, 0
failures), **43824896807** on `8ec4fa4` — success (536 tests, 0 failures; that run was the merge
commit of the branch head with `main`, which proved the branch merges cleanly with the PR3-025 work
before the rebase), and **43825323550** on `34772d9` — success (536 tests, 0 failures).

### The first run of the branch head failed, on a pre-existing flake

Attempt 1 of run **43826457955** on the branch head `379c17f` **failed**:

```
SettingsViewModelTest > testConnection success persists health snapshot FAILED
    java.lang.AssertionError at Assert.java:87
      Caused by: java.lang.AssertionError at SettingsViewModelTest.kt:222
536 tests completed, 1 failed
```

This is **not** caused by this branch:

- The failing file is `app/src/test/kotlin/.../ui/settings/SettingsViewModelTest.kt` and the assertion is
  at `SettingsViewModelTest.kt:222`. This branch touches no file under `ui/settings` and no settings code
  at all — `git log 560882c..HEAD -- app/src/test/.../ui/settings/` is empty.
- The head commit `379c17f` changed only `docs/STATUS.md` and this report — not one line of code.
- **The same test failed identically on `main`**, at `b686730`, in run **43816881326**
  (`495 tests completed, 1 failed`) — a commit that predates this branch.
- The test drives a MockWebServer call inside `runTest` + Turbine, so it races the virtual clock. It is
  green on `main` at `2904a44` and `560882c`, and green on this branch at `34772d9` and `cceb72f`.

The failed job was re-run (attempt 2 of the same run id) and is green: **536 tests, 0 failures, 0
ignored**.

The flake was **not** fixed here. It is unrelated code, and repairing it inside a register item about
work-order deferral would be scope creep. It is flagged for the operator: `SettingsViewModelTest` is
intermittently red and has now failed twice across `main` and PR runs.

### Every run of this branch, for the audit trail

| Run | Head | Result |
|---|---|---|
| 43824183321 | `bc711fa` | success — 524 tests, 0 failures |
| 43824896807 | `8ec4fa4` | success — 536 tests, 0 failures (merge commit with `main`, proving a clean merge with PR3-025) |
| 43825323550 | `34772d9` | success — 536 tests, 0 failures |
| 43825849231 | `cceb72f` | success — 536 tests, 0 failures |
| 43826457955 attempt 1 | `379c17f` | **failure** — 1 failure, `SettingsViewModelTest` (pre-existing flake, above) |
| 43826457955 attempt 2 | `379c17f` | **success** — 536 tests, 0 failures |

| Class | Tests | Failures | Ignored |
|---|---:|---:|---:|
| `…domain.usecase.WorkOrderDeferralTest` *(new)* | **12** | **0** | **0** |
| `…domain.usecase.CreateWorkOrderUseCaseTest` *(pre-existing)* | **1** | **0** | **0** |

The 12 new tests, all green:

```
PASS  the canonical deferral reason names BD-1-3 option B and PR3-027
PASS  the canonical deferral reason cites the contract clause that forbids the endpoint
PASS  the use case refuses with exactly the canonical reason
PASS  the use case has no dependency it could mint journal or send with
PASS  no production source outside the use case file references the use case
PASS  the use case is not provided by the Hilt graph
PASS  the gateway endpoint table defines no work-order creation route
PASS  the gateway api exposes no work-order creation method
PASS  the documented alternative is present in the plan detail screen
PASS  the documented alternative carries no action
PASS  the operator-facing text states where work orders come from
PASS  the operator-facing text and the domain reason agree on the deferral
```

The tests are source-level and structural rather than mock-only: they read the actual production files
(the same technique `BuildConfigStage31Test` uses on `libs.versions.toml` and `AndroidManifest.xml`) and
assert on the real repository state, so a regression — re-adding the Hilt provider, reintroducing an
`order` route, deleting the card, giving the card an action, or letting the two copies of the deferral
text drift — fails the build.

### Evidence NOT produced, and why

| Evidence | Why not produced |
|---|---|
| Android-runtime (device/emulator) evidence that the card renders | No device, emulator or Android SDK in this environment. The card's presence, its strings and its action-freeness are proven at the source level; the Compose render itself is **not** proven at runtime. `PR3-040` / `PR3-041` remain **OPEN** |
| Controlled-gateway integration evidence | No gateway environment is named by the operator. `PR3-042` remains **OPEN** and is out of scope for a deferral |
| Release / R8 build verification | Forbidden by plan rule G-01 |
| Evidence that work-order creation is unnecessary in practice | Not this item's claim. The claim is that the console does not and must not implement it |

---

## 7. Blockers, contract ambiguities and required operator / Claude actions

1. **One stale line in the evidence dossier was corrected.** The Stage 3.3 open item said the capability
   `order` identifier *"has no source in the gateway contract or the Task model … Needs an operator
   decision and a contract revision (PR3-003, PR3-027, BD-1.3)."* That decision and that contract revision
   have landed (ADR 0014 D1/D5; contract revision 1.2 / ADR 0015, §3.6). The line now states what is
   actually outstanding: the **Android client** does not carry `order` (`TaskSummaryDto` / `Task` have no
   such field) and the **gateway** does not yet return it (`gateway/src/routes/tasks.ts`). That is
   PR3-003 / PR3-021 work and was **not** done here. The correction was made because the line cited
   PR3-027 and would otherwise misrepresent this item's disposition.
2. **`PublishOrderResolver` still carries a stale KDoc.** It says the gateway's read endpoints *"carry no
   order identifier, and the contract defines no way to obtain one"*. Against revision 1.2 §3.6 that is no
   longer true. The file belongs to PR3-021 and was **not** touched here; PR3-021 must update it when it
   starts consuming the field.
3. **`docs/STATUS.md` header not touched.** The instruction was to change only this item's rows, so the
   document header's *Last updated* line still names the PR3-001 reconciliation of 2026-11-14. Flagged for
   the operator rather than rewritten.
4. **No protected file was needed.** Nothing in this item requires a change to `.github/workflows/**`,
   `gradle/libs.versions.toml`, `proguard-rules.pro`, `AndroidManifest.xml` or `app/build.gradle.kts`.
5. **The register was not modified.** PR3-027's acceptance text already describes this work; no register
   revision is needed.

---

## 8. Invariants touched

| Invariant | Effect | How it is preserved |
|---|---|---|
| **I-10 — Contract fidelity** | **Strengthened** | The console implements no work-order creation endpoint, and that is now asserted by a test rather than by inspection. The deferral wording is accurate against the current contract revision instead of claiming a contract gap that no longer exists |
| **I-08 — Fail closed** | **Strengthened** | The use case still fails closed; it is simply unreachable. It cannot mint, journal or send, because it holds no dependency that could |
| **I-02 — One minting path** | Unchanged | No capability is minted here. `OperationType.ORDER` remains unused |
| **I-03 / I-05 / I-06** | Unchanged | No dispatch, recovery or success path was touched |
| **I-01, I-04, I-07, I-09, I-11, I-12** | Unchanged | Not touched |

---

## 9. Overlap and conflict risk

- **PR #72 (PR3-025, in flight)** touches `DecideFrameworkUseCase.kt`, `DecisionScreen.kt`,
  `DecisionViewModel.kt`, `DecideFrameworkUseCaseAndViewModelTest.kt` and its own agent report. **No file
  overlap with this PR.**
- **PR #71 (PR3-010) was merged** into `main` as `2904a44` before this branch was created, so this branch
  already contains the journal AAD binding. No conflict.
- **No other open PR** existed when this branch was created.
- **`main` may move.** The `AIS` agent pushes directly to `main`. A concurrent change to
  `di/UseCaseModule.kt`, `ui/plans/PlanDetailScreen.kt` or `app/src/main/res/values/strings.xml` would
  need a rebase. The `strings.xml` change is two added lines and the `UseCaseModule` change removes one
  provider, so both rebase cleanly.
- **No gateway, contract, schema or workflow file** is touched, so there is no cross-workstream conflict.

---

## 10. Recommended next eligible item

**PR3-021** — Complete publish. It is Critical, and the two inputs it was blocked on have both landed:
the operator decision (ADR 0014 D1) and contract revision 1.2 (ADR 0015), which makes `order` a required
field of the `GET /tasks/next` item (§3.6). What remains is client-side: carry `order` on
`TaskSummaryDto` / `Task`, implement `PublishOrderResolver` against it, and update the stale
`PublishOrderResolver` KDoc noted in §7. The gateway's `tasks.ts` does not yet return the field, so
controlled-gateway evidence for publish still waits on the gateway route — the client work and its JVM
tests can proceed first.

*End of report.*
