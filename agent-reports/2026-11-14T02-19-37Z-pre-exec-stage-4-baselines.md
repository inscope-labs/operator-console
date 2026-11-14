# Pre-execution baselines — Stage 4.3 and Stage 4.4 mapped files

- **Author:** Claude (pre-execution baselines), at the operator's request.
- **Branch:** `scaffold/pre-execution-stage-4-baselines`.
- **Base:** `main` at `373b5f4` (merge of PR #63, the Phase 3 remediation register).
- **Files changed:** 5, all under `app/src/main/kotlin/com/inscopelabs/buildengine/`.
- **Stage:** none. This is preparation for Stages 4.3 and 4.4, not a stage.

## Assignment

Per the operator: generate the complete proposed code contents of the files in
`docs/PRE-EXECUTION-FILE-MAP.md`, to be used as proposed baselines when the AI Studio
agent executes the owning stage. The operator stated that this activity requires neither
CI nor compilation, and that the AI Studio agent performs all corrections,
realignments, compilation and testing.

The operator's instruction supersedes the onboarding document's "prepare a scaffold"
output type. Where the onboarding document's rules still protect the stage, they were
kept — see **Markers** below.

## Files

| Stage | Tasks | Path | Lines |
|---|---|---|---|
| 4.3 | T4-301 | `ui/submission/SubmissionReviewScreen.kt` | 980 |
| 4.4 | T4-401 | `ui/decision/DecisionHistoryScreen.kt` | 537 |
| 4.4 | T4-401 | `ui/audit/AuditInspectionScreen.kt` | 518 |
| 4.4 | T4-401 | `ui/device/DeviceManagementScreen.kt` | 651 |
| 4.4 | T4-401 | `ui/operations/HaltResumeScreen.kt` | 701 |

## Markers

- The `SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.` line was
  **replaced** with an accurate "Proposed baseline" statement. The literal line would
  have misdescribed the content: the files contain a full implementation, not an empty
  placeholder, and a completing agent that reads it as empty would discard the work.
- The `TODO(Stage X.Y / T…): …` line was **kept**, in every file, with content describing
  what the owning stage still owes. G-09 holds: the stage cannot be closed while the
  marker remains, so these files do not close Stages 4.3 or 4.4 by themselves.
- Both marker states are restated at the top of each file's KDoc.

## Assumptions

1. **The screens take state, not a ViewModel.** Each screen takes a `UiState<…>` plus
   plain callbacks, leaving the stage one seam: a `@HiltViewModel` exposing
   `StateFlow<UiState<…>>` and the mapping from the repositories to the screen's data
   type. This keeps each file self-contained against what is on `main` today. The
   onboarding rule "A signature you had to choose is labelled 'The signature below is a
   proposal'" applies to every one of these signatures, and each file says so.
2. **Nothing is wired.** No navigation entry, DI binding, manifest entry or caller was
   added (onboarding rule 06). `ui/navigation/Routes.kt` was not touched.
3. **No new dependency, resource or permission** (onboarding rule 05). The screens use
   only the Material 3 and Compose APIs already on the classpath. New copy is written as
   literals rather than added to `strings.xml`, because adding a resource would exceed
   the assigned paths; the stage should extract them.
4. **No `.mint(` and no crypto or signer imports** (onboarding rule 07). Verified by
   search across the five files.
5. **`AuditRecord` lives in `ui/audit/AuditInspectionScreen.kt`** and is imported by
   `ui/decision/DecisionHistoryScreen.kt`. Both read the same audit journal, so one
   model avoids two divergent copies. The stage may move it to `domain/model/`.

## Contract ground

Every endpoint, field and limit used here was read from the repository, not assumed:

- `GET /audit` and `GET /audit/{ref}` — `docs/gateway-contract.md` §3.20.
- `GET /admin/enrolments` and `POST /admin/revoke` — `gateway/docs/admin-surface.md`.
  The revoke confirmation string `REVOKE <kid>` is the gateway's own requirement.
- `POST /halt` and `POST /resume` — `docs/schemas/halt.schema.json`,
  `docs/schemas/resume.schema.json`; `branch` is constrained to the single value
  `engine`.
- `POST /tasks/{id}/upload` — `docs/schemas/upload.schema.json`; `submissionId` is the
  revision identity.
- 25 MiB archive cap — `gateway/src/config/constants.ts` (`MAX_UPLOAD_BYTES`).
- `PlanStatus.blockedTasks` — contract §3.3.

No endpoint, field or error code was invented (G-07).

## Findings raised for the operator and the stage

These are contract gaps, not implementation choices. They are recorded here rather than
resolved by guessing.

1. **The audit journal carries no decision rationale.** Contract §3.20 defines the audit
   record as `auditRef`, `timestamp`, `operation`, `plan`, `task`, `branch`, `headSha`,
   `kid`, `authTier`. The `notes` field exists on the `decide` *request*
   (`decide.schema.json`) and is not echoed into the audit read path. The decision
   history therefore shows the fields the contract exposes and says plainly that the
   rationale is not there. This is the same gap as PR3-003 question 1.
2. **The audit response has no published schema.** `docs/schemas/` contains no
   `audit.schema.json`, so the record shape comes from the §3.20 example alone. The stage
   must confirm it against the gateway before trusting it.
3. **The gateway's blocked-task reasons are not enumerated.** `PlanStatus.blockedTasks`
   is a `List<String>`. The screen defines the five categories the plan names
   (`missing input`, `spec conflict`, `decision needed`, `policy refusal`, `technical
   failure`) but does not map the gateway's strings onto them; the stage owns that
   mapping, and an unmapped reason must not be relabelled as one of the five.
4. **The halt cause is not in the gateway's audit record.** T3-401 requires a resume to
   carry the recorded cause. The screen sources the cause from the console's own
   operation journal (`OperationIntent.reason`) and refuses to offer a resume when the
   cause is unknown.
5. **`POST /plans/{id}/order` remains unimplementable.** The contract lists it in its §2
   table with no §3 section and no schema, so I-10 forbids the console from offering it.
   Nothing in these five files references it. This is PR3-027's documented deferral.

## Not verified

- **Not compiled.** No JDK, Gradle or Android SDK is available in the authoring
  environment, and the operator stated this assignment does not require compilation. The
  static checks that were run are listed below. Per G-04, "compiles in CI" is still the
  stage's bar, and the AI Studio agent's CI run on the pushed SHA is the only real
  verifier.
- **Not tested.** No test was added; tests are the owning stage's work (Gate 4 requires
  state tests).
- **Static checks performed:** bracket balance per file; every `com.inscopelabs.*` import
  resolved against a declaration in the repository; every non-project import checked for
  use (with `getValue`/`setValue` treated as property-delegation operators); no duplicate
  function names per file; no `.mint(`; no crypto, signer or Keystore imports; no
  credential material in any file.

## Scope note

`docs/PRE-EXECUTION-FILE-MAP.md` lists fifteen files across Stages 3.2, 4.3, 4.4, 5.1,
5.2, 5.3 and 5.4. This delivery covers the Stage 4 rows — 4.3 (T4-301) and 4.4 (T4-401)
— as the operator asked for "all the stage 4 files". The remaining mapped rows were left
alone deliberately:

- `domain/model/OperationOutcome.kt` (Stage 3.2, T3-201) was **completed in place** by
  Stage 3.2 and is no longer a scaffold. Recreating it would destroy implemented work,
  and G-14 forbids recreating a file a stage completes in place.
- The eight Stage 5 lab files (`lab/Scenario.kt`, `lab/ScenarioResult.kt`,
  `lab/ScenarioRunner.kt`, `lab/LabReport.kt`, `lab/Mutator.kt`,
  `lab/ScenarioCatalogue.kt`, `ui/lab/LabScreen.kt`, `ui/lab/LabViewModel.kt`) and
  `lab/ContractCheck.kt` belong to Stages 5.1–5.4 and are outside this delivery.

They can be delivered as a second branch on request.
