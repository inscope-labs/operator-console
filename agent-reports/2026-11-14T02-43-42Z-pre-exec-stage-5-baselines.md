# Pre-execution baselines — Stage 5 mapped files

- **Author:** Claude (pre-execution baselines), at the operator's request.
- **Branch:** `scaffold/pre-execution-stage-5-baselines`.
- **Base:** `main` at `33c9a11` (merge of PR #64, the Stage 4 baselines).
- **Files changed:** 9, all under `app/src/main/kotlin/com/inscopelabs/buildengine/`.
- **Stage:** none. This is preparation for Stages 5.1–5.4, not a stage.

## Assignment

The same assignment as PR #64: generate the complete proposed code contents of the
Stage 5 files in `docs/PRE-EXECUTION-FILE-MAP.md`, to be used as baselines when the AI
Studio agent executes the owning stage. No local compilation; the AI Studio agent
performs the corrections, realignments, compilation and testing.

## Files

| Stage | Tasks | Path | Lines |
|---|---|---|---|
| 5.1 | T5-101 | `lab/Scenario.kt` | 175 |
| 5.1 | T5-101 | `lab/ScenarioResult.kt` | 118 |
| 5.1 | T5-101 | `lab/ScenarioRunner.kt` | 430 |
| 5.1 | T5-102 | `lab/LabReport.kt` | 183 |
| 5.2 | T5-201 | `lab/Mutator.kt` | 464 |
| 5.2 | T5-202 | `lab/ScenarioCatalogue.kt` | 895 |
| 5.3 | T5-301..304 | `ui/lab/LabScreen.kt` | 633 |
| 5.3 | T5-301..304 | `ui/lab/LabViewModel.kt` | 230 |
| 5.4 | T5-401 | `lab/ContractCheck.kt` | 367 |

## Markers

As in PR #64: the `SCAFFOLD PLACEHOLDER` line was replaced with an accurate "Proposed
baseline" statement, and the `TODO(Stage X.Y / T…)` line was **kept** in every file, so
G-09 holds and the stages cannot close on this change alone.

## Assumptions

1. **The Lab screen takes state, not a ViewModel**, as in the Stage 4 files. The
   ViewModel is one of the mapped files, so it is delivered complete.
2. **No signer is imported.** The onboarding rule forbids importing signers or crypto,
   so `LabCapabilitySource` is the seam: it refuses every request until Stage 5.3
   (T5-302) replaces its body with the HMAC-backed implementation. Scenarios that need
   a capability are therefore *skipped and reported*, never silently run without one.
3. **Nothing is wired.** No navigation entry, DI binding, manifest entry or caller.
4. **No new dependency, resource or permission.** The Lab uses the Material 3, Compose
   and `kotlinx.serialization.json` APIs already on the classpath.
5. **The Hilt graph stays satisfiable.** `ScenarioRunner`, `LabCapabilitySource` and
   `LabViewModel` take only types that are either already bound (`GatewayHttpClient`,
   `Clock`) or carry an `@Inject constructor` of their own. No new module entry is
   required for this change to build.

## Contract ground

- Error catalogue: `gateway/src/errors/catalogue.ts` and `gateway/docs/error-catalogue.md`
  (38 frozen codes), with the HTTP status of each.
- Capability wire format: `docs/capability-format.md` §1 — ten LF-separated fields and
  a signature; field indexes used by the mutators.
- Mutation classes: `docs/fixtures/vectors/codec_mutations.json` (30 vectors) — the
  capability-level mutators mirror them one for one.
- Response shapes: `docs/schemas/*.json` — branch-head, operation-lookup, plans-list,
  tasks-next, health, and the acknowledgement shape.
- Lab rules: ADR 0004 (environment guard, lab signing, banner, in-memory only),
  BD-5.1, BD-5.2, and RELEASE-PLAN.md Stage 5.1–5.4.

No endpoint, field or error code was invented (G-07).

## Findings raised for the operator and the stage

1. **`BRANCH_NOT_TASK` is not a frozen code, and three places still use it.**
   The frozen catalogue's alias table maps it to `BRANCH_INVALID`, and its freeze rule
   says the conformance vectors and the client `ErrorMapper` must reference the frozen
   names only. But `docs/fixtures/vectors/codec_mutations.json` still expects
   `BRANCH_NOT_TASK`, and `ErrorMapper` still carries
   `GATEWAY_BRANCH_NOT_TASK`. The scenario for this case expects the frozen
   `BRANCH_INVALID`, and `ContractCheck` reports `ParentAliasInUse` if the gateway
   sends the alias. **The vectors and the `ErrorMapper` need a separate correction
   against the freeze rule; that is not this stage's file.**
2. **The contract's response field tables are hand-transcribed.** `docs/schemas/` is
   not shipped inside the app, so `ContractCheck.RESPONSE_SHAPES` is transcribed by
   hand. A hand-maintained table is a second contract that can drift from the first —
   the very thing the check exists to catch. The stage should generate the table from
   the published schemas.
3. **The transport exposes no way to set a request header.** `GatewayHttpClient.request`
   sets the bearer credential and the body and nothing else. A Lab mutation therefore
   cannot act on a header, and the CRLF mutation acts on a body field instead. A
   header-level mutation needs a transport that exposes headers.
4. **A 2xx status is not observable.** The transport returns the body only on success,
   so the exact status of an accepted request is unknown; the runner records 200 and
   the report says so.
5. **Twenty-one of the thirty-eight refusals are fixture-dependent.** The client cannot
   provoke `REPO_NOT_PRIVATE`, `HALT_ACTIVE`, `RATE_BUDGET_LOW`, `NETWORK`,
   `CONFIG_INVALID` and the rest on demand: they depend on how the gateway is
   configured. Each such scenario carries a `precondition` and is **skipped** with that
   precondition quoted. The report counts skipped scenarios separately from passes.
6. **Packaging is not Stage 5.3's to fix.** The Lab files are in the main source set
   because that is where the scaffolds are; BD-5.1's exclusion of Lab code from the
   release build is Stage 5.5 / T5-501, and this change does not attempt it.

## Not verified

- **Not compiled.** No JDK, Gradle or Android SDK is available in the authoring
  environment. Static checks performed: bracket balance per file; every
  `com.inscopelabs.*` import resolved against a declaration in the repository; every
  non-project import checked for use; no duplicate declarations; the
  `ArchitectureTest` `.mint(` rule simulated across the whole main source set; the
  catalogue's coverage of all 38 frozen error codes asserted mechanically.
- **Not tested.** The stage writes the tests, including the T5-202 unit test that
  asserts every registry error code has a scenario.
- **The Lab has never been run against a gateway.** No scenario has been executed and
  no expectation has been observed to hold. The catalogue's expectations are read from
  the contract and the vectors, not from a live run.
