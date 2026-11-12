# Pre-execution file map

Approved by the operator (2026-11-12), from the new-code file register verified at commit `9adbfe2`
(19 proposed new Kotlin files). Agents convert only files listed here
(`docs/PRE-EXECUTION-ONBOARDING.md`). Filenames marked *proposed* are organisation choices: the plan
names the class or view, not the file, and the completing stage may fold a file into an existing one.

## Scaffolded now (15)

| Stage | Tasks | Path under `app/src/main/kotlin/com/inscopelabs/buildengine/` | Filename |
|---|---|---|---|
| 3.2 | T3-201 | `domain/model/OperationOutcome.kt` | proposed |
| 4.3 | T4-301 | `ui/submission/SubmissionReviewScreen.kt` | proposed |
| 4.4 | T4-401 | `ui/decision/DecisionHistoryScreen.kt` | proposed |
| 4.4 | T4-401 | `ui/audit/AuditInspectionScreen.kt` | proposed |
| 4.4 | T4-401 | `ui/device/DeviceManagementScreen.kt` | proposed |
| 4.4 | T4-401 | `ui/operations/HaltResumeScreen.kt` | proposed |
| 5.1 | T5-101 | `lab/Scenario.kt`, `lab/ScenarioResult.kt`, `lab/ScenarioRunner.kt` | class named by plan |
| 5.1 | T5-102 | `lab/LabReport.kt` | class named by plan |
| 5.2 | T5-201 | `lab/Mutator.kt` | class named by plan |
| 5.2 | T5-202 | `lab/ScenarioCatalogue.kt` | class named by plan |
| 5.3 | T5-301..304 | `ui/lab/LabScreen.kt`, `ui/lab/LabViewModel.kt` | class named by plan |
| 5.4 | T5-401 | `lab/ContractCheck.kt` | class named by plan |

## Deferred (4): not scaffolded until Stage 3.1's dependency PR merges

`data/local/OperationJournalEntity.kt`, `data/local/OperationJournalDao.kt`,
`data/local/OperationJournalDatabase.kt` (T3-202, T3-101) and `data/worker/OperationRecoveryWorker.kt`
(T3-203, T3-205). They need Room and WorkManager, which are not in `gradle/libs.versions.toml` yet.

## Not in this map
Phase 6 files (the plan names no classes; a design/file mapping comes first), tests, and every existing
file that only needs completing (see the register's exclusion table).
